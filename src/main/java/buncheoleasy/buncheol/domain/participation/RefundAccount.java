package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.user.domain.BankAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 분철이 진행되지 않을 때(취소) 참여자에게 환불할 계좌. 정산 계좌({@link BankAccount})와 형식·길이 검증 규칙은 동일하지만, participations
 * 테이블의 {@code refund_*} 컬럼에 매핑되는 별도 VO 다 (User 의 정산 계좌는 {@code settlement_*} 컬럼이라 컬럼이 다르다). 검증은
 * {@link BankAccount#validate} 로 공유한다.
 */
@Embeddable
public record RefundAccount(
    @Column(name = "refund_bank", length = 50, nullable = false, updatable = false) String bank,
    @Column(name = "refund_account", length = 50, nullable = false, updatable = false)
        String account,
    @Column(name = "refund_holder", length = 50, nullable = false, updatable = false)
        String holder) {

  public RefundAccount {
    BankAccount.validate(bank, account, holder);
  }

  public static RefundAccount of(final String bank, final String account, final String holder) {
    return new RefundAccount(bank, account, holder);
  }
}
