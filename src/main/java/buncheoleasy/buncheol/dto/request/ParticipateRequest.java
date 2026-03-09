package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.ParticipationType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ParticipateRequest(
    @NotNull Long buncheolMemberId,
    @NotNull Long shippingAddressId,
    @NotNull ParticipationType type,
    Long bidAmount) {

  @AssertTrue(message = "BID 타입은 제시 금액이 필수이며, INSTANT 타입은 제시 금액을 입력할 수 없습니다.")
  public boolean isBidAmountValid() {
    if (type == ParticipationType.BID) {
      return bidAmount != null && bidAmount > 0;
    }
    if (type == ParticipationType.INSTANT) {
      return bidAmount == null;
    }
    return true;
  }
}
