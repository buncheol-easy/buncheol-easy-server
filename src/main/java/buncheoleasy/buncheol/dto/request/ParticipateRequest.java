package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.RefundAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ParticipateRequest(
    @NotNull Long buncheolMemberId,
    @NotNull Long shippingAddressId,
    @NotNull @Valid RefundAccountRequest refundAccount) {

  public RefundAccount toRefundAccount() {
    return refundAccount.toRefundAccount();
  }
}
