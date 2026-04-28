package buncheoleasy.buncheol.domain.member;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record BidOption(
    @Column(name = "bid_allowed", nullable = false) boolean bidAllowed,
    @Column(name = "bid_min_price") Long bidMinPrice) {

  public static BidOption of(
      final long instantPrice, final boolean bidAllowed, final Long bidMinPrice) {
    validate(instantPrice, bidAllowed, bidMinPrice);
    return new BidOption(bidAllowed, bidMinPrice);
  }

  private static void validate(
      final long instantPrice, final boolean bidAllowed, final Long bidMinPrice) {
    validateBidMinPricePresence(bidAllowed, bidMinPrice);
    validateBidMinPriceRange(instantPrice, bidAllowed, bidMinPrice);
  }

  private static void validateBidMinPricePresence(
      final boolean bidAllowed, final Long bidMinPrice) {
    if (bidAllowed && bidMinPrice == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_REQUIRED);
    }
    if (!bidAllowed && bidMinPrice != null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_FORBIDDEN);
    }
  }

  private static void validateBidMinPriceRange(
      final long instantPrice, final boolean bidAllowed, final Long bidMinPrice) {
    if (bidAllowed && bidMinPrice != null && (bidMinPrice <= 0 || bidMinPrice >= instantPrice)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
    }
  }
}
