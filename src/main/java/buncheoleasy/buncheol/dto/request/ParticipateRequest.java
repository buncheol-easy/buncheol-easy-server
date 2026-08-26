package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.RefundAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 분철 참여 신청 요청. 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)이므로 {@code buncheolMemberId} 로 슬롯 하나를 지정한다. 오픈 이벤트 운영
 * 정책으로 분철당 활성 참여는 1건만 허용한다 (취소·만료된 참여는 재참여 가능).
 *
 * @param refundAccount 취소 시 환불받을 계좌. <b>0원 참여(코드 참여)에서는 생략할 수 있다</b> — 환불할 돈이 없어 계좌를 받을 이유가
 *     없고, 받으면 서포터즈에게 존재하지 않는 보증금 조건이 있는 것처럼 보인다. 유상 참여에서 생략하면 서비스가 거부한다.
 * @param participationCode 코드 참여 슬롯({@code CODE_ONLY})에 참여할 때 제출하는 코드. 선착순 슬롯에 보내면 거부한다.
 */
public record ParticipateRequest(
    @NotNull Long buncheolMemberId,
    @NotNull Long shippingAddressId,
    @Valid RefundAccountRequest refundAccount,
    @Size(max = 32) String participationCode) {

  public ParticipateRequest(
      final Long buncheolMemberId,
      final Long shippingAddressId,
      final RefundAccountRequest refundAccount) {
    this(buncheolMemberId, shippingAddressId, refundAccount, null);
  }

  public RefundAccount toRefundAccount() {
    return refundAccount == null ? null : refundAccount.toRefundAccount();
  }
}
