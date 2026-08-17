package buncheoleasy.buncheol.dto.response;

import buncheoleasy.user.domain.BankAccount;

/**
 * 참여자가 입금할 개최자 계좌. 입금 대기(AWAITING_PAYMENT·PAYMENT_SENT) 단계에서만 노출한다. C2C 신청(APPLIED) 단계는 계좌가 없어
 * null 을 그대로 내린다 (docs/46 §3-1·§3-5).
 */
public record HostAccountResponse(String bank, String account, String holder) {

  public static HostAccountResponse from(final BankAccount account) {
    return account == null
        ? null
        : new HostAccountResponse(account.bank(), account.account(), account.holder());
  }
}
