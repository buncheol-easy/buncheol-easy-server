package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.RefundAccount;

/** 참여자가 입력한 환불 계좌. 분철 취소 시 운영자가 이 계좌로 환불한다. */
public record RefundAccountResponse(String bank, String account, String holder) {

  public static RefundAccountResponse from(final RefundAccount account) {
    return new RefundAccountResponse(account.bank(), account.account(), account.holder());
  }
}
