package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.RefundAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 분철 참여 신청 요청. 한 번에 여러 멤버 슬롯을 선착순으로 점유할 수 있다(UI 다중 선택). 배송비는 묶음당 1회만 부과되므로 한 요청으로 묶어 신청해야 중복으로 내지
 * 않는다. 한 분철의 멤버 슬롯 수를 넘을 수 없으므로 트랜잭션 길이 방어 차원의 상한만 둔다.
 */
public record ParticipateRequest(
    @NotEmpty @Size(max = 50) List<@NotNull Long> buncheolMemberIds,
    @NotNull Long shippingAddressId,
    @NotNull @Valid RefundAccountRequest refundAccount) {

  public RefundAccount toRefundAccount() {
    return refundAccount.toRefundAccount();
  }
}
