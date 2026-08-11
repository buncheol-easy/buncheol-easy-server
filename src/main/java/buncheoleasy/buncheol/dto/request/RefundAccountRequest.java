package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.user.domain.BankAccount;
import jakarta.validation.constraints.NotBlank;

/**
 * 분철이 진행되지 않을 때(취소) 환불받을 참여자 본인 계좌. 형식 검증은 {@link RefundAccount} 생성 시 수행하고, 신규 입력 경로라 계좌번호 최소
 * 자릿수까지 여기서 함께 본다 (docs/53 Q-02).
 */
public record RefundAccountRequest(
    @NotBlank String bank, @NotBlank String account, @NotBlank String holder) {

  public RefundAccount toRefundAccount() {
    BankAccount.validateForRegistration(bank, account, holder);
    return RefundAccount.of(bank, account, holder);
  }
}
