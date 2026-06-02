package buncheoleasy.payment.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "participation_id", nullable = false, updatable = false)
  private Long participationId;

  // PAYMENT(결제) | REFUND(환불). REFUND 는 parentPaymentId 가 가리키는 PAYMENT 의 환불 트랜잭션.
  @Enumerated(EnumType.STRING)
  @Column(name = "tx_type", nullable = false, length = 20, updatable = false)
  private PaymentTxType txType;

  // 우리 시스템에서 발급해 Toss 로 전달하는 결제 주문 ID (멱등성 키 역할).
  @Column(name = "order_id", nullable = false, length = 100, updatable = false)
  private String orderId;

  // Toss 가 승인 단계에 발급하는 결제 키. PENDING 상태에선 NULL, CONFIRMING 진입 시 채워짐.
  @Column(name = "payment_key", length = 200)
  private String paymentKey;

  // REFUND 트랜잭션이 가리키는 원본 PAYMENT id. PAYMENT 트랜잭션이면 NULL.
  @Column(name = "parent_payment_id", updatable = false)
  private Long parentPaymentId;

  // 거래 금액(원, 양수). 환불도 양수로 기록.
  @Column(nullable = false, updatable = false)
  private long amount;

  // PENDING | CONFIRMING | DONE | FAILED.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  // 실패/환불 사유 메시지.
  @Column(length = 255)
  private String reason;

  // 결제 요청(PENDING 진입) 시각.
  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  // Toss 승인 완료(DONE 진입) 또는 환불 완료 시각. 미완료 상태에선 NULL.
  @Column(name = "approved_at")
  private Instant approvedAt;

  public static Payment createPayment(
      final Long participationId, final String orderId, final long amount, final Instant now) {
    validateCreation(participationId, orderId, amount);
    return new Payment(
        participationId,
        PaymentTxType.PAYMENT,
        orderId,
        null,
        null,
        amount,
        PaymentStatus.PENDING,
        null,
        now,
        null);
  }

  /**
   * 결제 수단 확정 전 mock 결제용 팩토리. PENDING/CONFIRMING 단계 없이 즉시 완료(DONE)된 결제를 기록한다. PG 미연동이라 {@code
   * paymentKey} 는 null 이며, 추후 실제 PG 도입 시 정상 결제 흐름(createPayment → 승인)으로 대체된다.
   */
  public static Payment createCompleted(
      final Long participationId, final String orderId, final long amount, final Instant now) {
    validateCreation(participationId, orderId, amount);
    return new Payment(
        participationId,
        PaymentTxType.PAYMENT,
        orderId,
        null,
        null,
        amount,
        PaymentStatus.DONE,
        null,
        now,
        now);
  }

  private static void validateCreation(
      final Long participationId, final String orderId, final long amount) {
    if (participationId == null) {
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
      final String orderId,
      final String paymentKey,
      final Long parentPaymentId,
      final long amount,
      final PaymentStatus status,
      final String reason,
      final Instant requestedAt,
      final Instant approvedAt) {
    this.participationId = participationId;
    this.txType = txType;
    this.orderId = orderId;
    this.paymentKey = paymentKey;
    this.parentPaymentId = parentPaymentId;
    this.amount = amount;
    this.status = status;
    this.reason = reason;
    this.requestedAt = requestedAt;
    this.approvedAt = approvedAt;
  }

  public void startConfirm(final String paymentKey) {
    if (status != PaymentStatus.PENDING) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    this.paymentKey = paymentKey;
    this.status = PaymentStatus.CONFIRMING;
  }

  public void completeConfirm(final Instant now) {
    if (status != PaymentStatus.CONFIRMING) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
    this.status = PaymentStatus.DONE;
    this.approvedAt = now;
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
