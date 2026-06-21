package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.RefundAccount;
import jakarta.validation.constraints.NotBlank;

/** 분철이 진행되지 않을 때(취소) 환불받을 참여자 본인 계좌. 형식 검증은 {@link RefundAccount} 생성 시 수행한다. */
public record RefundAccountRequest(
    @NotBlank String bank, @NotBlank String account, @NotBlank String holder) {

  public RefundAccount toRefundAccount() {
    return RefundAccount.of(bank, account, holder);
  }
}
