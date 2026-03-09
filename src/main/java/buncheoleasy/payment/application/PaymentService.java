package buncheoleasy.payment.application;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.domain.Payment;
import buncheoleasy.payment.domain.PaymentDomainService;
import buncheoleasy.payment.domain.PaymentPhase;
import buncheoleasy.payment.infrastructure.TossPaymentClient;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmRejectedException;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmResponse;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmUnavailableException;
import buncheoleasy.payment.infrastructure.TossPaymentsProperties;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class PaymentService {

  private final PaymentDomainService paymentDomainService;
  private final PaymentCompletionHandler paymentCompletionHandler;
  private final TossPaymentClient tossPaymentClient;
  private final TossPaymentsProperties tossPaymentsProperties;
  private final TransactionTemplate transactionTemplate;

  @Value("${app.base-url}")
  private String baseUrl;

  public PaymentService(
      final PaymentDomainService paymentDomainService,
      final PaymentCompletionHandler paymentCompletionHandler,
      final TossPaymentClient tossPaymentClient,
      final TossPaymentsProperties tossPaymentsProperties,
      final PlatformTransactionManager transactionManager) {
    this.paymentDomainService = paymentDomainService;
    this.paymentCompletionHandler = paymentCompletionHandler;
    this.tossPaymentClient = tossPaymentClient;
    this.tossPaymentsProperties = tossPaymentsProperties;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public PaymentOrderInfo createPaymentOrder(
      final Long participationId,
      final PaymentPhase paymentPhase,
      final long amount,
      final String paymentOrderName) {
    final Payment existingPaymentOrder = getReusablePayment(participationId, paymentPhase, amount);

    if (existingPaymentOrder != null) {
      return toPaymentOrderInfo(existingPaymentOrder, paymentOrderName);
    }

    final Payment paymentOrder =
        Payment.createPayment(participationId, paymentPhase, generatePaymentOrderId(), amount);
    paymentDomainService.create(paymentOrder);
    return toPaymentOrderInfo(paymentOrder, paymentOrderName);
  }

  public void confirmPayment(
      final String paymentKey, final String paymentOrderId, final long amount) {
    final ConfirmationPreparation preparation =
        transactionTemplate.execute(
            status -> prepareConfirmation(paymentKey, paymentOrderId, amount));

    if (preparation == null) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    if (preparation.alreadyDone()) {
      return;
    }

    final TossConfirmResponse tossResponse;
    try {
      tossResponse = tossPaymentClient.confirm(paymentKey, paymentOrderId, amount);
    } catch (TossConfirmRejectedException e) {
      final String failReason = buildFailReason(e.errorCode(), e.getMessage());
      transactionTemplate.executeWithoutResult(
          status -> failConfirmingPayment(paymentOrderId, paymentKey, failReason));
      throw new BusinessException(ErrorCode.PAYMENT_TOSS_CONFIRM_FAILED);
    } catch (TossConfirmUnavailableException e) {
      // TODO: CONFIRMING 장기 잔류 결제를 토스 조회로 재조정하는 reconciliation 로직 추가
      log.warn(
          "토스 결제 승인 결과 확인 불가: paymentOrderId={}, code={}, message={}",
          paymentOrderId,
          e.errorCode(),
          e.getMessage());
      throw new BusinessException(ErrorCode.PAYMENT_TOSS_CONFIRM_FAILED);
    }

    if (tossResponse.totalAmount() != amount) {
      log.error(
          "토스 결제 승인 금액 불일치: paymentOrderId={}, expectedAmount={}, actualAmount={}",
          paymentOrderId,
          amount,
          tossResponse.totalAmount());
      throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    transactionTemplate.executeWithoutResult(
        status -> completeConfirmation(paymentOrderId, paymentKey));

    log.info(
        "결제 승인 성공: paymentOrderId={}, paymentKey={}, method={}, approvedAt={}",
        paymentOrderId,
        paymentKey,
        tossResponse.method(),
        tossResponse.approvedAt());
  }

  @Transactional
  public void cancelPendingPayment(
      final Long userId, final String paymentOrderId, final String code, final String message) {
    final Payment payment = paymentDomainService.getPaymentByOrderId(paymentOrderId);
    paymentCompletionHandler.validateOwnership(payment.getParticipationId(), userId);

    if (payment.isFailed()) {
      return;
    }

    if (payment.isDone()) {
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_APPROVED);
    }

    if (!payment.isPending()) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    final String failReason = buildFailReason(code, message);
    payment.fail(failReason);
    paymentDomainService.updatePending(payment);
    paymentCompletionHandler.onPaymentFailed(
        payment.getParticipationId(), payment.getPaymentPhase(), failReason);
    log.info("결제 취소 처리: paymentOrderId={}, reason={}", paymentOrderId, failReason);
  }

  private Payment getReusablePayment(
      final Long participationId, final PaymentPhase paymentPhase, final long expectedAmount) {
    final Payment payment =
        paymentDomainService
            .findLatestPaymentByParticipationIdAndPaymentPhase(participationId, paymentPhase)
            .orElse(null);

    if (payment == null) {
      return null;
    }
    if (payment.isPending()) {
      if (payment.getAmount() != expectedAmount) {
        payment.fail("참여 조건 변경으로 기존 결제 주문 만료");
        paymentDomainService.updatePending(payment);
        return null;
      }
      return payment;
    }
    if (payment.isConfirming()) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    if (payment.isDone()) {
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_APPROVED);
    }
    return null;
  }

  private PaymentOrderInfo toPaymentOrderInfo(
      final Payment paymentOrder, final String paymentOrderName) {
    return new PaymentOrderInfo(
        tossPaymentsProperties.clientKey(),
        paymentOrder.getOrderId(),
        paymentOrderName,
        paymentOrder.getAmount(),
        baseUrl + "/v1/payments/success",
        baseUrl + "/v1/payments/fail");
  }

  private void validateAlreadyApprovedPayment(final Payment payment, final String paymentKey) {
    if (payment.getPaymentKey() != null && payment.getPaymentKey().equals(paymentKey)) {
      return;
    }
    throw new BusinessException(ErrorCode.PAYMENT_ALREADY_APPROVED);
  }

  private void validateConfirmingPayment(final Payment payment, final String paymentKey) {
    if (payment.getPaymentKey() != null && payment.getPaymentKey().equals(paymentKey)) {
      return;
    }
    throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
  }

  private String buildFailReason(final String code, final String message) {
    final String safeCode = code == null || code.isBlank() ? "UNKNOWN" : code;
    final String safeMessage = message == null || message.isBlank() ? "결제 실패" : message;
    return safeCode + ": " + safeMessage;
  }

  private ConfirmationPreparation prepareConfirmation(
      final String paymentKey, final String paymentOrderId, final long amount) {
    final Payment payment = paymentDomainService.getPaymentByOrderId(paymentOrderId);

    if (payment.isDone()) {
      validateAlreadyApprovedPayment(payment, paymentKey);
      log.info("이미 승인된 결제 (멱등 처리): paymentOrderId={}", paymentOrderId);
      return ConfirmationPreparation.done();
    }

    if (payment.getAmount() != amount) {
      throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    if (payment.isPending()) {
      payment.startConfirm(paymentKey);
      paymentDomainService.updatePending(payment);
      return ConfirmationPreparation.ready();
    }

    if (payment.isConfirming()) {
      validateConfirmingPayment(payment, paymentKey);
      log.info("진행 중인 결제 승인 재시도: paymentOrderId={}", paymentOrderId);
      return ConfirmationPreparation.ready();
    }

    throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
  }

  private void completeConfirmation(final String paymentOrderId, final String paymentKey) {
    final Payment payment = paymentDomainService.getPaymentByOrderId(paymentOrderId);

    if (payment.isDone()) {
      validateAlreadyApprovedPayment(payment, paymentKey);
      return;
    }

    if (!payment.isConfirming()) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    validateConfirmingPayment(payment, paymentKey);
    payment.completeConfirm();
    paymentDomainService.updateConfirming(payment);
    paymentCompletionHandler.onPaymentCompleted(
        payment.getParticipationId(), payment.getPaymentPhase());
  }

  private void failConfirmingPayment(
      final String paymentOrderId, final String paymentKey, final String failReason) {
    final Payment payment = paymentDomainService.getPaymentByOrderId(paymentOrderId);

    if (payment.isDone()) {
      validateAlreadyApprovedPayment(payment, paymentKey);
      return;
    }

    if (payment.isFailed()) {
      return;
    }

    if (!payment.isConfirming()) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    validateConfirmingPayment(payment, paymentKey);
    payment.failConfirm(failReason);
    paymentDomainService.updateConfirming(payment);
    paymentCompletionHandler.onPaymentFailed(
        payment.getParticipationId(), payment.getPaymentPhase(), failReason);
  }

  private String generatePaymentOrderId() {
    return "order_" + UUID.randomUUID().toString().replace("-", "");
  }

  private record ConfirmationPreparation(boolean alreadyDone) {

    private static ConfirmationPreparation done() {
      return new ConfirmationPreparation(true);
    }

    private static ConfirmationPreparation ready() {
      return new ConfirmationPreparation(false);
    }
  }
}
