package buncheoleasy.payment.application;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.domain.Payment;
import buncheoleasy.payment.domain.PaymentDomainService;
import buncheoleasy.payment.infrastructure.TossPaymentClient;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmRejectedException;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmResponse;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmUnavailableException;
import buncheoleasy.payment.infrastructure.TossPaymentsProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private static final String FAIL_REASON_PAYMENT_ORDER_EXPIRED = "참여 조건 변경으로 기존 결제 주문 만료";

  private final PaymentDomainService paymentDomainService;
  private final PaymentCompletionHandler paymentCompletionHandler;
  private final TossPaymentClient tossPaymentClient;
  private final TossPaymentsProperties tossPaymentsProperties;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  @Value("${app.base-url}")
  private String baseUrl;

  @Transactional
  public PaymentOrderInfo createPaymentOrder(
      final Long participationId, final long amount, final String paymentOrderName) {
    Payment existingPaymentOrder = getReusablePayment(participationId, amount);

    if (existingPaymentOrder != null) {
      return toPaymentOrderInfo(existingPaymentOrder, paymentOrderName);
    }

    Payment paymentOrder =
        Payment.createPayment(
            participationId, generatePaymentOrderId(), amount, Instant.now(clock));
    paymentDomainService.create(paymentOrder);
    return toPaymentOrderInfo(paymentOrder, paymentOrderName);
  }

  /**
   * 결제 수단 확정 전 mock 결제: Toss 승인 단계 없이 즉시 완료(DONE)된 결제를 기록한다. {@code amount} 는 제시가 + 배송비. 추후 실제 PG
   * 흐름으로 교체된다.
   */
  @Transactional
  public void recordMockPayment(final Long participationId, final long amount) {
    Payment payment =
        Payment.createCompleted(
            participationId, generatePaymentOrderId(), amount, Instant.now(clock));
    paymentDomainService.create(payment);
  }

  public void confirmPayment(
      final String paymentKey, final String paymentOrderId, final long amount) {
    boolean alreadyDone = prepareConfirmation(paymentKey, paymentOrderId, amount);
    if (alreadyDone) {
      return;
    }

    TossConfirmResponse tossResponse;
    try {
      tossResponse = tossPaymentClient.confirm(paymentKey, paymentOrderId, amount);
    } catch (TossConfirmRejectedException e) {
      /** Toss에서 거절한 승인 요청 예외 (error-code: 4xx) */
      String failReason = buildFailReason(e.errorCode(), e.getMessage());

      // payment 객체 fail처리
      transactionTemplate.executeWithoutResult(
          status -> failConfirmingPayment(paymentOrderId, paymentKey, failReason));

      throw new BusinessException(ErrorCode.PAYMENT_TOSS_CONFIRM_REJECTED);
    } catch (TossConfirmUnavailableException e) {
      // TODO: 해당 예외 발생 시 payment 는 confirming 으로 남는데, 스케줄러로 이 confirming payment 보정 작업 해줘야 함.
      /**
       * 알 수 없는 예외 (예: 네트워크 통신 실패, 토스 서버 장애 등) 실제 Toss 서버에서는 결제 승인이 되었는데 돌아오는 네트워크에서 장애가 난 것일 수 있기
       * 때문에 fail처리 해서는 안됨.
       */
      log.warn(
          "토스 결제 승인 결과 불확실: Payment_status=CONFIRMING 유지, paymentOrderId={}, code={}, message={}",
          paymentOrderId,
          e.errorCode(),
          e.getMessage());
      throw new BusinessException(ErrorCode.PAYMENT_TOSS_CONFIRM_UNAVAILABLE);
    }

    if (tossResponse.totalAmount() != amount) {
      log.error(
          "토스 결제 승인 금액 불일치: paymentOrderId={}, expectedAmount={}, actualAmount={}",
          paymentOrderId,
          amount,
          tossResponse.totalAmount());
      // TODO: 금액 불일치 발생 시 결제 조회/보정 또는 자동 환불 정책 추가 (거의 발생하지는 않지만 방어 로직)
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
    Payment payment = paymentDomainService.getPaymentByOrderId(paymentOrderId);
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

    String failReason = buildFailReason(code, message);
    payment.fail(failReason);
    paymentDomainService.updatePendingPayment(payment);
    paymentCompletionHandler.onPaymentFailed(payment.getParticipationId(), failReason);
    log.info("결제 취소 처리: paymentOrderId={}, reason={}", paymentOrderId, failReason);
  }

  private Payment getReusablePayment(final Long participationId, final long expectedAmount) {
    Payment payment =
        paymentDomainService.findLatestByParticipationId(participationId).orElse(null);

    if (payment == null) {
      return null;
    }
    if (payment.isPending()) {
      if (payment.getAmount() != expectedAmount) {
        payment.fail(FAIL_REASON_PAYMENT_ORDER_EXPIRED);
        paymentDomainService.updatePendingPayment(payment);
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

  private String buildFailReason(final String code, final String message) {
    String safeCode = code == null || code.isBlank() ? "UNKNOWN" : code;
    String safeMessage = message == null || message.isBlank() ? "결제 실패" : message;
    return safeCode + ": " + safeMessage;
  }

  private boolean prepareConfirmation(
      final String paymentKey, final String paymentOrderId, final long amount) {
    Payment payment = paymentDomainService.getPaymentByOrderId(paymentOrderId);

    // 결제가 이미 결제 완료 상태이면?
    if (payment.isDone()) {
      validateSamePaymentKey(payment, paymentKey, ErrorCode.PAYMENT_ALREADY_APPROVED);
      log.info("이미 승인된 결제 (멱등 처리): paymentOrderId={}", paymentOrderId);
      return true;
    }

    // 금액이 다르면 이상한 요청
    if (payment.getAmount() != amount) {
      throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    // payment가 pending 상태일 경우: 해당 payment 객체의 첫 결제 승인 요청
    if (payment.isPending()) {
      payment.startConfirm(paymentKey);
      paymentDomainService.updatePendingPayment(payment);
      return false;
    }

    // payment가 confirming 상태일 경우: 해당 payment 객체에 결제 승인을 시도했던 적이 있고 그 요청이 실패하여, 결제 승인 중으로 남아있는 상태
    if (payment.isConfirming()) {
      validateSamePaymentKey(payment, paymentKey, ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
      log.info("실패 했던 결제 승인 재시도: paymentOrderId={}", paymentOrderId);
      return false;
    }

    throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
  }

  private void completeConfirmation(final String paymentOrderId, final String paymentKey) {
    Payment payment = paymentDomainService.getPaymentByOrderId(paymentOrderId);

    if (payment.isDone()) {
      // 이 요청이 이미 완료된 같은 결제건의 재호출인가?
      validateSamePaymentKey(payment, paymentKey, ErrorCode.PAYMENT_ALREADY_APPROVED);
      return;
    }

    // 정상 흐름이라면 현 시점에 payment가 confirming 상태여야함.
    if (!payment.isConfirming()) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    // 지금 처리 중인 결제와 같은 paymentKey 요청인가?
    validateSamePaymentKey(payment, paymentKey, ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);

    payment.completeConfirm(Instant.now(clock));
    paymentDomainService.updateConfirmingPayment(payment);
    paymentCompletionHandler.onPaymentCompleted(payment.getParticipationId());
  }

  private void failConfirmingPayment(
      final String paymentOrderId, final String paymentKey, final String failReason) {
    Payment payment = paymentDomainService.getPaymentByOrderId(paymentOrderId);

    // 결제가 이미 결제 완료 상태이면?
    if (payment.isDone()) {
      validateSamePaymentKey(payment, paymentKey, ErrorCode.PAYMENT_ALREADY_APPROVED);
      return;
    }

    if (payment.isFailed()) {
      return;
    }

    if (!payment.isConfirming()) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    // 지금 처리 중인 결제와 같은 paymentKey 요청인가?
    validateSamePaymentKey(payment, paymentKey, ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);

    payment.failConfirm(failReason);
    paymentDomainService.updateConfirmingPayment(payment);
    paymentCompletionHandler.onPaymentFailed(payment.getParticipationId(), failReason);
  }

  /**
   * 현재 들어온 결제 객체에 대한 승인 요청이, 이미 해당 결제 객체로 승인한 결제 요청인지 확인 아닐 경우, 한 결제 객체는 하나의 결제 승인을 가져야 하기 때문에 예외
   * 맞을 경우, 따닥과 같은 같은 결제에 대한 결제 승인 중복 요청으로 예외는 아님.
   */
  private void validateSamePaymentKey(
      final Payment payment, final String paymentKey, final ErrorCode errorCode) {
    if (payment.getPaymentKey() != null && payment.getPaymentKey().equals(paymentKey)) {
      return;
    }
    throw new BusinessException(errorCode);
  }

  private String generatePaymentOrderId() {
    return "order_" + UUID.randomUUID().toString().replace("-", "");
  }
}
