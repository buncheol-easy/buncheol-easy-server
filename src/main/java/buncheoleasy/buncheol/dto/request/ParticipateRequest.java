package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.RefundAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 분철 참여 신청 요청. 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)이므로 {@code buncheolMemberId} 로 슬롯 하나를 지정한다. 여러 멤버를 원하면
 * 참여를 여러 번 반복하며, 배송비는 참여마다 부과된다.
 */
public record ParticipateRequest(
    @NotNull Long buncheolMemberId,
    @NotNull Long shippingAddressId,
    @NotNull @Valid RefundAccountRequest refundAccount) {

  public RefundAccount toRefundAccount() {
    return refundAccount.toRefundAccount();
  }
}
