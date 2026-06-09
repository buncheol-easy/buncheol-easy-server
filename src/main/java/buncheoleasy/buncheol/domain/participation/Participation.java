package buncheoleasy.buncheol.domain.participation;

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
@Table(name = "participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participation extends TimestampedEntity {

  private static final String FAIL_REASON_NOT_SELECTED = "낙찰 실패";
  private static final String FAIL_REASON_PAYMENT_OVERDUE = "입금 기한 초과";

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

  // 구매자가 '입금 완료'를 신고한 시각 (계좌이체 수동확인 MVP). PAYMENT_REPORTED 진입 시 세팅, reject 시 NULL.
  @Column(name = "payment_reported_at")
  private Instant paymentReportedAt;

  // 개최자가 입금을 확인한 시각. 수동확인으로 CONFIRMED 진입 시 세팅.
  @Column(name = "payment_confirmed_at")
  private Instant paymentConfirmedAt;

  // ACTIVE_BID | AWAITING_PAYMENT | PAYMENT_REPORTED | CONFIRMED | CANCELLED | FAILED.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ParticipationStatus status;

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

  public void validateOwnedBy(final Long participantId) {
    if (!this.participantId.equals(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }
  }

  // ACTIVE_BID → AWAITING_PAYMENT: 마감 시 멤버 슬롯별 최고가 낙찰자로 선정되어 결제 요청 대상이 된다.
  public void awardAsWinner(final Instant dueAt) {
    if (status != ParticipationStatus.ACTIVE_BID) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.AWAITING_PAYMENT;
    this.closedRank = 1;
    this.dueAt = dueAt;
  }

  // ACTIVE_BID 유지 + 제시가 순위만 부여: 마감 시 2순위 이하를 차순위 승계 후보로 남긴다.
  public void assignClosedRank(final int closedRank) {
    if (status != ParticipationStatus.ACTIVE_BID) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.closedRank = closedRank;
  }

  // ACTIVE_BID → FAILED: 마감 시 낙찰되지 못한 참여. 제시가 순위(closedRank)도 함께 기록한다.
  // (계좌이체 MVP 의 happy-path 마감에선 미사용 — 2순위 이하는 assignClosedRank 로 ACTIVE_BID 유지한다.)
  public void markNotSelected(final int closedRank, final Instant now) {
    if (status != ParticipationStatus.ACTIVE_BID) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.FAILED;
    this.closedRank = closedRank;
    this.failReason = FAIL_REASON_NOT_SELECTED;
    this.finalizedAt = now;
  }

  // AWAITING_PAYMENT → CONFIRMED: 낙찰자 결제 완료 (PG 결제 흐름). 수동입금 MVP 에선 confirmManualPayment 사용.
  public void completePayment(final Instant now) {
    if (status != ParticipationStatus.AWAITING_PAYMENT) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.CONFIRMED;
    this.finalizedAt = now;
    this.failReason = null;
  }

  // AWAITING_PAYMENT → PAYMENT_REPORTED: (계좌이체 MVP) 구매자가 개최자 계좌로 송금 후 '입금 완료'를 신고.
  public void reportPayment(final Instant now) {
    if (status != ParticipationStatus.AWAITING_PAYMENT) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    // 입금 기한(dueAt) 이 없거나 지난 뒤에는 신고할 수 없다 (만료/차순위 이양 대상).
    if (dueAt == null || now.isAfter(dueAt)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_DUE_PASSED);
    }
    this.status = ParticipationStatus.PAYMENT_REPORTED;
    this.paymentReportedAt = now;
  }

  // PAYMENT_REPORTED → CONFIRMED: (계좌이체 MVP) 개최자가 실제 입금을 확인. PG 용 completePayment 와 분리한다.
  public void confirmManualPayment(final Instant now) {
    if (status != ParticipationStatus.PAYMENT_REPORTED) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.CONFIRMED;
    this.paymentConfirmedAt = now;
    this.finalizedAt = now;
    this.failReason = null;
  }

  // AWAITING_PAYMENT(입금 기한 경과) → FAILED: 미입금 낙찰자 만료. 차순위 승계의 선행 단계다.
  // status 가드로 PAYMENT_REPORTED/CONFIRMED 는 만료 대상에서 자동 제외되고, dueAt 미경과 시에도 막는다.
  public void expireUnpaid(final Instant now) {
    if (status != ParticipationStatus.AWAITING_PAYMENT) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    if (dueAt == null || !now.isAfter(dueAt)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_NOT_DUE_YET);
    }
    this.status = ParticipationStatus.FAILED;
    this.failReason = FAIL_REASON_PAYMENT_OVERDUE;
    this.finalizedAt = now;
  }

  // ACTIVE_BID → AWAITING_PAYMENT: 만료된 낙찰자를 대신해 차순위 후보를 승계한다. 새 입금 기한(dueAt)만 부여하고
  // closedRank(마감 시점 순위)는 감사/추적 정보로 보존한다 — 현재 결제 대상 여부는 status 로 판단한다.
  public void promoteToWinner(final Instant dueAt) {
    if (status != ParticipationStatus.ACTIVE_BID) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.AWAITING_PAYMENT;
    this.dueAt = dueAt;
  }

  // PAYMENT_REPORTED → AWAITING_PAYMENT: 개최자가 입금 불일치로 반려. 구매자가 dueAt 내 다시 신고할 수 있게 되돌린다.
  public void rejectPayment() {
    if (status != ParticipationStatus.PAYMENT_REPORTED) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.AWAITING_PAYMENT;
    this.paymentReportedAt = null;
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

  // ACTIVE_BID → CANCELLED: 참여자 본인의 자발적 참여 취소
  public void cancel(final Instant now) {
    if (status != ParticipationStatus.ACTIVE_BID) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    this.status = ParticipationStatus.CANCELLED;
    this.finalizedAt = now;
  }
}
