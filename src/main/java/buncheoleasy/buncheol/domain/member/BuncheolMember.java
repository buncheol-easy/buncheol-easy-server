package buncheoleasy.buncheol.domain.member;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class BuncheolMember {

  private static final int MEMBER_NAME_MAX_LENGTH = 100;

  private Long id;
  private final Long buncheolId;
  private final Long memberId;
  private final String memberName;
  private long instantPrice;
  private final BidOption bidOption;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static BuncheolMember create(
      final Long buncheolId,
      final Long memberId,
      final String memberName,
      final long instantPrice,
      final boolean bidAllowed,
      final Long bidMinPrice) {
    return new BuncheolMember(
        buncheolId, memberId, memberName, instantPrice, bidAllowed, bidMinPrice);
  }

  private BuncheolMember(
      final Long buncheolId,
      final Long memberId,
      final String memberName,
      final long instantPrice,
      final boolean bidAllowed,
      final Long bidMinPrice) {
    validate(buncheolId, memberName, instantPrice);
    this.buncheolId = buncheolId;
    this.memberId = memberId;
    this.memberName = memberName;
    this.instantPrice = instantPrice;
    this.bidOption = BidOption.of(instantPrice, bidAllowed, bidMinPrice);
  }

  // MyBatis 조회 전용 생성자
  private BuncheolMember(
      final Long id,
      final Long buncheolId,
      final Long memberId,
      final String memberName,
      final long instantPrice,
      final boolean bidAllowed,
      final Long bidMinPrice,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt) {
    this.id = id;
    this.buncheolId = buncheolId;
    this.memberId = memberId;
    this.memberName = memberName;
    this.instantPrice = instantPrice;
    this.bidOption = new BidOption(bidAllowed, bidMinPrice);
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void validateBidAllowed() {
    if (!bidOption.bidAllowed()) {
      throw new BusinessException(ErrorCode.PARTICIPATION_BID_NOT_ALLOWED);
    }
  }

  public void validateBidAmount(final long bidAmount) {
    Long bidMinPrice = bidOption.bidMinPrice();
    if (bidMinPrice == null || bidAmount < bidMinPrice || bidAmount >= instantPrice) {
      throw new BusinessException(ErrorCode.PARTICIPATION_BID_AMOUNT_INVALID);
    }
  }

  private void validate(final Long buncheolId, final String memberName, final long instantPrice) {
    validateBuncheolId(buncheolId);
    validateMemberName(memberName);
    validateInstantPrice(instantPrice);
  }

  private void validateBuncheolId(final Long buncheolId) {
    if (buncheolId == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateMemberName(final String memberName) {
    if (memberName == null || memberName.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (memberName.length() > MEMBER_NAME_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_NAME_LENGTH_INVALID);
    }
  }

  private void validateInstantPrice(final long instantPrice) {
    if (instantPrice <= 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);
    }
  }
}
