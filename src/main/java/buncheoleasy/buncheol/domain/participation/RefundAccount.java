package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.user.domain.BankAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 분철이 진행되지 않을 때(취소) 참여자에게 환불할 계좌 + 개최자 통장 대조 키(입금자명).
 *
 * <p>🔴 <b>정본은 {@link ParticipationBundle} 이다</b> (P2-c). {@code participations.refund_*} 에는 더 이상 매핑되지
 * 않으며 그 컬럼은 P4 에서 삭제된다 — <b>이 PR 이후 새로 만들어지는 참여 행의 세 칸은 전부 NULL 이다.</b>
 *
 * <p>따라서 이 VO 가 {@code null} 이라는 것의 뜻도 바뀌었다: 예전엔 "계좌 강제(#151) 이전 0원 참여" 였지만
 * 이제는 <b>"묶음 미연결"</b> 이다 — 배포선 창에서 생긴 행이거나 P2-b 이전 잔여 행이다. 읽는 경로(개최 관리·내
 * 참여·알림·어드민)는 계속 null 을 확인할 것. 정산 계좌({@link BankAccount})와 형식·길이 검증 규칙은 동일하지만, participations
 * 테이블의 {@code refund_*} 컬럼에 매핑되는 별도 VO 다 (User 의 정산 계좌는 {@code settlement_*} 컬럼이라 컬럼이 다르다). 검증은
 * {@link BankAccount#validate} 로 공유한다.
 *
 * <p><b>참여 생성은 금액과 무관하게 이 계좌를 요구한다</b> (docs/80 결정 1) — 0원 참여도 예외가 아니다. 환불할 돈이 없어도
 * 예금주가 개최자 통장 대조 키이고, 묶음({@code participation_bundles.refund_*})이 NOT NULL 이기 때문이다.
 *
 * <p>다만 <b>참여 계좌 강제(PR #151) 이전에 만들어진 0원 참여는 이 필드가 null 이다</b>. 이 클래스가 그 경계의 정본이다 —
 * 옛 행을 읽을 수 있는 경로(개최 관리·내 참여·알림)는 계속 null 을 확인할 것. 컬럼의 NOT NULL 원복은 P4 에서 컬럼을
 * 삭제하며 정리한다.
 *
 * <p>ℹ️ 0원 참여자(서포터즈)에게도 계좌를 요구하므로 <b>수집·보관 범위가 넓어진다</b>. "예금주만 받는다" 는 절충이
 * 불가능해서다 — 세 칸은 all-or-nothing 이고(아래), 묶음 컬럼이 NOT NULL 이라 부분 채움은 그 행을 읽는 것 자체를
 * 깨뜨린다. P4 에서 이 컬럼이 삭제되고 정본이 묶음으로 옮겨갈 때 재검토 대상이다.
 *
 * <p><b>계좌를 갖는 것과 내보내는 것은 별개다.</b> 0원 참여도 이 값을 갖지만 대조할 입금이 없어 개최자에게는 내리지 않는다
 * — 노출 판정은 계좌 유무가 아니라 <b>금액</b>이다 ({@code BuncheolManagementQueryService#depositorNameOf}).
 *
 * <p>⚠️ 세 칸은 all-or-nothing 이다 — 전부 null 이면 조회가 정상이지만 <b>하나라도 남으면 생성자 검증이 예외를 던진다</b>.
 * 부분 채움을 만들지 말 것.
 */
@Embeddable
public record RefundAccount(
    @Column(name = "refund_bank", length = 50, updatable = false) String bank,
    @Column(name = "refund_account", length = 50, updatable = false) String account,
    @Column(name = "refund_holder", length = 50, updatable = false) String holder) {

  public RefundAccount {
    BankAccount.validate(bank, account, holder);
  }

  public static RefundAccount of(final String bank, final String account, final String holder) {
    return new RefundAccount(bank, account, holder);
  }
}
