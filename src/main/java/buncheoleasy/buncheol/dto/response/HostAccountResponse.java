package buncheoleasy.buncheol.dto.response;

import buncheoleasy.user.domain.BankAccount;

/** 참여자가 입금할 개최자 계좌. 입금확인중(AWAITING_PAYMENT) 단계에서만 노출한다. */
public record HostAccountResponse(String bank, String account, String holder) {

  public static HostAccountResponse from(final BankAccount account) {
    return new HostAccountResponse(account.bank(), account.account(), account.holder());
  }
}
