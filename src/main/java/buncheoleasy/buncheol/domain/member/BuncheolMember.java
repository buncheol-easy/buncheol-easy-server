package buncheoleasy.buncheol.domain.member;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buncheol_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuncheolMember extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  // group_members FK.
  @Column(name = "member_id", nullable = false, updatable = false)
  private Long memberId;

  // 호스트가 설정한 제시 최소 금액. 모든 참여는 BID 이며 이 금액 이상으로 제시해야 한다. 상한은 없다.
  @Column(name = "bid_min_price", nullable = false)
  private long bidMinPrice;

  public static BuncheolMember create(
      final Long buncheolId, final Long memberId, final long bidMinPrice) {
    return new BuncheolMember(buncheolId, memberId, bidMinPrice);
  }

  private BuncheolMember(final Long buncheolId, final Long memberId, final long bidMinPrice) {
    validate(buncheolId, memberId, bidMinPrice);
    this.buncheolId = buncheolId;
    this.memberId = memberId;
    this.bidMinPrice = bidMinPrice;
  }

  public void updateBidMinPrice(final long newBidMinPrice) {
    validateBidMinPrice(newBidMinPrice);
    this.bidMinPrice = newBidMinPrice;
  }

  public void validateBidAmount(final long bidAmount) {
    if (bidAmount < bidMinPrice) {
      throw new BusinessException(ErrorCode.PARTICIPATION_BID_AMOUNT_INVALID);
    }
  }

  private void validate(final Long buncheolId, final Long memberId, final long bidMinPrice) {
    validateBuncheolId(buncheolId);
    validateMemberId(memberId);
    validateBidMinPrice(bidMinPrice);
  }

  private void validateBuncheolId(final Long buncheolId) {
    if (buncheolId == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateMemberId(final Long memberId) {
    if (memberId == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateBidMinPrice(final long bidMinPrice) {
    if (bidMinPrice <= 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
    }
  }
}
