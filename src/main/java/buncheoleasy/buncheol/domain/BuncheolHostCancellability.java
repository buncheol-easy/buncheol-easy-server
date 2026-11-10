package buncheoleasy.buncheol.domain;

/**
 * 개최자의 분철 취소 가능 여부 판정 결과 (docs/56 S-2).
 *
 * <p>취소 API 의 게이트({@code BuncheolService#cancelBuncheol})와 개최 목록 응답({@code
 * MyHostedBuncheolResponse})이 <b>같은 판정을 공유</b>하도록 예외 대신 값으로 표현한다 — {@code
 * ParticipationCancellability}·{@code C2cHostQualification} 과 같은 형태다. 목록 화면이 상태 집합으로 자체 판정하다 사실과
 * 다른 안내를 띄운 적이 있어(docs/56 §21-4) 판정을 서버 한 곳에 둔다.
 *
 * <p>판정 → 표현(에러코드 · 안내 문구) 변환은 호출부에 둔다.
 */
public enum BuncheolHostCancellability {
  /** 취소 가능 — 모집중 · 인원미달 자동취소(숨김). */
  CANCELLABLE,
  /** 상태상 취소 불가 — 진행확정(CONFIRMED) 이후와 이미 개최자 취소된 분철 (BCH-050). */
  BLOCKED_BY_STATUS,
  /**
   * 입금 수집중 — 참여자에게 계좌가 공개된 뒤라 개최자 혼자 접지 않고 운영진과 함께 접는다(운영진 파기). 입금 확인 건수와 무관하다
   * (BCH-126). 운영진 분철은 이 상태가 없다.
   */
  NEEDS_STAFF;

  /** 상태만 본다. {@code null}(조회 결과 없음)은 취소 불가로 둔다. */
  public static BuncheolHostCancellability of(final BuncheolStatus status) {
    if (status == BuncheolStatus.PAYMENT_COLLECTING) {
      return NEEDS_STAFF;
    }
    // 취소 CAS 가 시도하는 상태와 반드시 일치해야 한다 (BuncheolDomainService#cancelBuncheol).
    // 어긋나면 목록이 "취소 가능" 이라 말한 카드가 409 로 떨어지므로, 전 상태를 도는 테스트로 못 박는다.
    if (status == BuncheolStatus.RECRUITING || status == BuncheolStatus.CANCELLED) {
      return CANCELLABLE;
    }
    return BLOCKED_BY_STATUS;
  }

  public boolean isCancellable() {
    return this == CANCELLABLE;
  }
}
