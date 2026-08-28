package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.RefundAccount;

/**
 * 참여자의 환불 계좌(마이페이지 정산 계좌 스냅샷). 분철 취소 시 이 계좌로 환불한다.
 *
 * <p>2026-08-28 이후 참여는 금액과 무관하게 계좌를 갖는다(docs/80 결정 1). 그 이전에 만들어진 0원 참여만 {@code null} 이다.
 */
public record RefundAccountResponse(String bank, String account, String holder) {

  public static RefundAccountResponse from(final RefundAccount account) {
    return account == null
        ? null
        : new RefundAccountResponse(account.bank(), account.account(), account.holder());
  }
}
