package buncheoleasy.payment.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "participation_id", nullable = false, updatable = false)
  private Long participationId;

  // PAYMENT(결제) | REFUND(환불). REFUND 는 parentPaymentId 가 가리키는 PAYMENT 의 환불 트랜잭션.
  @Enumerated(EnumType.STRING)
  @Column(name = "tx_type", nullable = false, length = 20, updatable = false)
  private PaymentTxType txType;

  // INSTANT(즉시구매 전액) | DEPOSIT(BID 신청 시 보증금) | BALANCE(BID 확정 후 잔금).
  @Enumerated(EnumType.STRING)
  @Column(name = "payment_phase", nullable = false, length = 20, updatable = false)
  private PaymentPhase paymentPhase;

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
  private LocalDateTime requestedAt;

  // Toss 승인 완료(DONE 진입) 또는 환불 완료 시각. 미완료 상태에선 NULL.
  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
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

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
