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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participation {

  // 일반 save 경로 외에 JpaParticipationRepositoryAdapter 의 conditional INSERT 흐름에서
  // ReflectionUtils 로 직접 주입되는 필드. 필드명·타입 변경 시 어댑터의 정적 ID_FIELD 초기화도 함께 갱신할 것.
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

  // 참여자가 제시한 금액. 멤버의 bid_min_price 이상이어야 한다.
  @Column(name = "bid_amount", nullable = false, updatable = false)
  private Long bidAmount;

  // 낙찰자 결제 마감 시각 (UTC). 이 시각까지 미결제 시 FAILED 처리 후 차순위로 권한 이양.
  // 차순위로 이양될 때마다 갱신되므로 buncheol.deadline 으로부터 도출하지 않고 행마다 보관한다.
  @Column(name = "due_at")
  private Instant dueAt;

  // 분철 마감 시점 제시가 순위. 1위부터 멤버 슬롯 수만큼 CONFIRMED 후보로 선정.
  @Column(name = "closed_rank")
  private Integer closedRank;

  // FAILED 사유. 예: 낙찰 실패, 결제 미진행 등. CONFIRMED/ACTIVE 상태에선 NULL.
  @Column(name = "fail_reason", length = 100)
  private String failReason;

  // 참여가 CONFIRMED 또는 FAILED 로 최종 결정된 시각. 그 외 상태에선 NULL.
  @Column(name = "finalized_at")
  private Instant finalizedAt;

  // ACTIVE_BID | AWAITING_PAYMENT | CONFIRMED | CANCELLED | FAILED.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ParticipationStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static Participation create(
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
        bidAmount,
        ParticipationStatus.ACTIVE_BID);
  }

  private Participation(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final long bidAmount,
      final ParticipationStatus status) {
    validate(bidAmount);
    this.buncheolId = buncheolId;
    this.buncheolMemberId = buncheolMemberId;
    this.participantId = participantId;
    this.shippingAddressId = shippingAddressId;
    this.bidAmount = bidAmount;
    this.status = status;
  }

  private void validate(final long bidAmount) {
    if (bidAmount <= 0) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  // AWAITING_PAYMENT → CONFIRMED: 낙찰자 결제 완료
  public void completePayment(final Instant now) {
    if (status != ParticipationStatus.AWAITING_PAYMENT) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.CONFIRMED;
    this.finalizedAt = now;
    this.failReason = null;
  }

  // ACTIVE_BID/AWAITING_PAYMENT → FAILED: 낙찰 실패 또는 결제 미진행
  public void fail(final String reason, final Instant now) {
    if (status != ParticipationStatus.ACTIVE_BID
        && status != ParticipationStatus.AWAITING_PAYMENT) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.FAILED;
    this.failReason = reason;
    this.finalizedAt = now;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
