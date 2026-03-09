package buncheoleasy.payment.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class Payment {

  private Long id;
  private final Long participationId;
  private final PaymentTxType txType;
  private final PaymentPhase paymentPhase;
  private final String orderId;
  private String paymentKey;
  private final Long parentPaymentId;
  private final long amount;
  private PaymentStatus status;
  private String reason;
  private final LocalDateTime requestedAt;
  private LocalDateTime approvedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Payment createPayment(
      final Long participationId,
      final PaymentPhase paymentPhase,
      final String orderId,
      final long amount) {
    validateCreation(participationId, paymentPhase, orderId, amount);
    return new Payment(
        participationId,
        PaymentTxType.PAYMENT,
        paymentPhase,
        orderId,
        null,
        null,
        amount,
        PaymentStatus.PENDING,
        null,
        LocalDateTime.now(),
        null);
  }

  private static void validateCreation(
      final Long participationId,
      final PaymentPhase paymentPhase,
      final String orderId,
      final long amount) {
    if (participationId == null) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    if (paymentPhase == null) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    if (orderId == null || orderId.isBlank()) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    if (amount <= 0) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  private Payment(
      final Long participationId,
      final PaymentTxType txType,
      final PaymentPhase paymentPhase,
      final String orderId,
      final String paymentKey,
      final Long parentPaymentId,
      final long amount,
      final PaymentStatus status,
      final String reason,
      final LocalDateTime requestedAt,
      final LocalDateTime approvedAt) {
    this.participationId = participationId;
    this.txType = txType;
    this.paymentPhase = paymentPhase;
    this.orderId = orderId;
    this.paymentKey = paymentKey;
    this.parentPaymentId = parentPaymentId;
    this.amount = amount;
    this.status = status;
    this.reason = reason;
    this.requestedAt = requestedAt;
    this.approvedAt = approvedAt;
  }

  // MyBatis 조회 전용 생성자
  private Payment(
      final Long id,
      final Long participationId,
      final PaymentTxType txType,
      final PaymentPhase paymentPhase,
      final String orderId,
      final String paymentKey,
      final Long parentPaymentId,
      final long amount,
      final PaymentStatus status,
      final String reason,
      final LocalDateTime requestedAt,
      final LocalDateTime approvedAt,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt) {
    this.id = id;
    this.participationId = participationId;
    this.txType = txType;
    this.paymentPhase = paymentPhase;
    this.orderId = orderId;
    this.paymentKey = paymentKey;
    this.parentPaymentId = parentPaymentId;
    this.amount = amount;
    this.status = status;
    this.reason = reason;
    this.requestedAt = requestedAt;
    this.approvedAt = approvedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void startConfirm(final String paymentKey) {
    if (status != PaymentStatus.PENDING) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    this.paymentKey = paymentKey;
    this.status = PaymentStatus.CONFIRMING;
  }

  public void completeConfirm() {
    if (status != PaymentStatus.CONFIRMING) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    this.status = PaymentStatus.DONE;
    this.approvedAt = LocalDateTime.now();
  }

  public void fail(final String reason) {
    if (status != PaymentStatus.PENDING) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    this.status = PaymentStatus.FAILED;
    this.reason = reason;
  }

  public void failConfirm(final String reason) {
    if (status != PaymentStatus.CONFIRMING) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    this.status = PaymentStatus.FAILED;
    this.reason = reason;
  }

  public boolean isPending() {
    return status == PaymentStatus.PENDING;
  }

  public boolean isDone() {
    return status == PaymentStatus.DONE;
  }

  public boolean isConfirming() {
    return status == PaymentStatus.CONFIRMING;
  }

  public boolean isFailed() {
    return status == PaymentStatus.FAILED;
  }
}
