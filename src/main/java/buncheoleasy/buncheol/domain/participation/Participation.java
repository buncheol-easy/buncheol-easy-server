package buncheoleasy.buncheol.domain.participation;

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
@Table(name = "participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  // 참여한 멤버 슬롯 (buncheol_members.id).
  @Column(name = "buncheol_member_id", nullable = false, updatable = false)
  private Long buncheolMemberId;

  // 참여한 유저 (users.id).
  @Column(name = "participant_id", nullable = false, updatable = false)
  private Long participantId;

  // 참여 시점에 선택한 배송지 (shipping_addresses.id). 분철 마감 후엔 변경 불가.
  @Column(name = "shipping_address_id", nullable = false)
  private Long shippingAddressId;

  // INSTANT(즉시구매) | BID(제시).
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private ParticipationType type;

  // INSTANT 참여 시점의 instant_price 스냅샷 (이후 호스트가 가격을 바꿔도 이 값은 불변). BID 면 NULL.
  @Column(name = "instant_price_snapshot", updatable = false)
  private Long instantPriceSnapshot;

  // BID 참여자가 제시한 금액. INSTANT 면 NULL.
  @Column(name = "bid_amount")
  private Long bidAmount;

  // BID 가 마감 시점에 확정된 후, 인기 멤버 가격 보정으로 추가 결제해야 하는 차액. 잔금 결제(BALANCE phase)의 금액.
  @Column(name = "balance_due_amount")
  private Long balanceDueAmount;

  // 잔금 결제 마감 시각 (KST). 이 시각까지 미결제 시 FAILED 처리 대상.
  @Column(name = "balance_due_at")
  private LocalDateTime balanceDueAt;

  // 분철 마감 시점 BID 참여의 제시가 순위. 1위부터 멤버 슬롯 수만큼 CONFIRMED 로 확정.
  @Column(name = "closed_rank")
  private Integer closedRank;

  // FAILED 사유. 예: "결제 실패", "즉시 구매 확정으로 인한 자동 실패 처리", 잔금 미결제 등. CONFIRMED/ACTIVE 상태에선 NULL.
  @Column(name = "fail_reason", length = 100)
  private String failReason;

  // 참여가 CONFIRMED 또는 FAILED 로 최종 결정된 시각. 그 외 상태에선 NULL.
  @Column(name = "finalized_at")
  private LocalDateTime finalizedAt;

  // PAYMENT_PENDING | ACTIVE_BID | AWAITING_BALANCE_PAYMENT | CONFIRMED | CANCELLED | FAILED.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ParticipationStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static Participation createInstant(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final long instantPriceSnapshot) {
    return new Participation(
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        ParticipationType.INSTANT,
        instantPriceSnapshot,
        null,
        ParticipationStatus.PAYMENT_PENDING);
  }

  public static Participation createBid(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final long bidAmount) {
    return new Participation(
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        ParticipationType.BID,
        null,
        bidAmount,
        ParticipationStatus.PAYMENT_PENDING);
  }

  private Participation(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final ParticipationType type,
      final Long instantPriceSnapshot,
      final Long bidAmount,
      final ParticipationStatus status) {
    validate(type, instantPriceSnapshot, bidAmount);
    this.buncheolId = buncheolId;
    this.buncheolMemberId = buncheolMemberId;
    this.participantId = participantId;
    this.shippingAddressId = shippingAddressId;
    this.type = type;
    this.instantPriceSnapshot = instantPriceSnapshot;
    this.bidAmount = bidAmount;
    this.status = status;
  }

  private void validate(
      final ParticipationType type, final Long instantPriceSnapshot, final Long bidAmount) {
    if (type == ParticipationType.INSTANT) {
      if (instantPriceSnapshot == null || instantPriceSnapshot <= 0 || bidAmount != null) {
        throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
      }
      return;
    }

    if (type == ParticipationType.BID) {
      if (instantPriceSnapshot != null || bidAmount == null || bidAmount <= 0) {
        throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
      }
      return;
    }

    throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
  }

  // PAYMENT_PENDING → ACTIVE_BID: 예치금 결제 완료 후 제시 활성화 (BID 전용)
  public void activateBidAfterDepositPayment() {
    if (type != ParticipationType.BID || status != ParticipationStatus.PAYMENT_PENDING) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.ACTIVE_BID;
    this.failReason = null;
    this.finalizedAt = null;
  }

  // PAYMENT_PENDING → CONFIRMED: 즉시 구매 결제 완료 (INSTANT 전용)
  public void confirmInstantParticipation(final LocalDateTime now) {
    if (type != ParticipationType.INSTANT || status != ParticipationStatus.PAYMENT_PENDING) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.CONFIRMED;
    this.finalizedAt = now;
    this.failReason = null;
  }

  // AWAITING_BALANCE_PAYMENT → CONFIRMED: 잔금 결제 완료
  public void confirmBalancePayment(final LocalDateTime now) {
    if (status != ParticipationStatus.AWAITING_BALANCE_PAYMENT) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.CONFIRMED;
    this.finalizedAt = now;
    this.failReason = null;
  }

  // PAYMENT_PENDING/ACTIVE_BID/AWAITING_BALANCE_PAYMENT → FAILED: 결제 실패 또는 외부 요인
  public void fail(final String reason, final LocalDateTime now) {
    if (status != ParticipationStatus.ACTIVE_BID
        && status != ParticipationStatus.PAYMENT_PENDING
        && status != ParticipationStatus.AWAITING_BALANCE_PAYMENT) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.FAILED;
    this.failReason = reason;
    this.finalizedAt = now;
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
