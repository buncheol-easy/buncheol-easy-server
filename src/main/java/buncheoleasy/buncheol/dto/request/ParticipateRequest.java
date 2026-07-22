package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.RefundAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 분철 참여 신청 요청. 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)이므로 {@code buncheolMemberId} 로 슬롯 하나를 지정한다. 오픈 이벤트 운영
 * 정책으로 분철당 활성 참여는 1건만 허용한다 (취소·만료된 참여는 재참여 가능).
 */
public record ParticipateRequest(
    @NotNull Long buncheolMemberId,
    @NotNull Long shippingAddressId,
    @NotNull @Valid RefundAccountRequest refundAccount) {

  public RefundAccount toRefundAccount() {
    return refundAccount.toRefundAccount();
  }
}
