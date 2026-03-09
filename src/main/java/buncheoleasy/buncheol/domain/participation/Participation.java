package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class Participation {

  private Long id;
  private final Long buncheolId;
  private final Long buncheolMemberId;
  private final Long participantId;
  private Long shippingAddressId;
  private final ParticipationType type;
  private final Long instantPriceSnapshot;
  private Long bidAmount;
  private Long balanceDueAmount;
  private LocalDateTime balanceDueAt;
  private Integer closedRank;
  private String failReason;
  private LocalDateTime finalizedAt;
  private ParticipationStatus status;
  private LocalDateTime createdAt;
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
    this.balanceDueAmount = null;
    this.balanceDueAt = null;
    this.closedRank = null;
    this.failReason = null;
    this.finalizedAt = null;
    this.status = status;
  }

  // MyBatis 조회 전용 생성자
  private Participation(
      final Long id,
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final ParticipationType type,
      final Long instantPriceSnapshot,
      final Long bidAmount,
      final Long balanceDueAmount,
      final LocalDateTime balanceDueAt,
      final Integer closedRank,
      final String failReason,
      final LocalDateTime finalizedAt,
      final ParticipationStatus status,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt) {
    this.id = id;
    this.buncheolId = buncheolId;
    this.buncheolMemberId = buncheolMemberId;
    this.participantId = participantId;
    this.shippingAddressId = shippingAddressId;
    this.type = type;
    this.instantPriceSnapshot = instantPriceSnapshot;
    this.bidAmount = bidAmount;
    this.balanceDueAmount = balanceDueAmount;
    this.balanceDueAt = balanceDueAt;
    this.closedRank = closedRank;
    this.failReason = failReason;
    this.finalizedAt = finalizedAt;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
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

  // BID 타입 + PAYMENT_PENDING/ACTIVE_BID 상태에서만 제시 수정 가능
  public void updateBid(final long newBidAmount, final Long newShippingAddressId) {
    if (type != ParticipationType.BID
        || (status != ParticipationStatus.PAYMENT_PENDING
            && status != ParticipationStatus.ACTIVE_BID)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_BID_UPDATE_NOT_ALLOWED);
    }
    if (newShippingAddressId == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    this.bidAmount = newBidAmount;
    this.shippingAddressId = newShippingAddressId;
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
}
