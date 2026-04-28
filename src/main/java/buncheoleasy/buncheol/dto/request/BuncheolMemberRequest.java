package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BuncheolMemberRequest(
    Long buncheolMemberId,
    @NotNull Long memberId,
    @Positive long instantPrice,
    boolean bidAllowed,
    @Positive Long bidMinPrice) {

  // 제시 허용일 경우 제시 최소 금액 필수
  @AssertTrue
  public boolean isBidMinPriceValidWhenBidAllowed() {
    if (!bidAllowed) {
      return true;
    }
    return bidMinPrice != null;
  }
}
