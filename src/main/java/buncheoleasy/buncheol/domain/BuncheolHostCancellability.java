package buncheoleasy.buncheol.domain;

/**
 * 개최자의 분철 취소 가능 여부 판정 결과 (docs/56 H-13 · S-2).
 *
 * <p>취소 API 의 게이트({@code BuncheolService#cancelBuncheol})와 개최 목록 응답({@code
 * MyHostedBuncheolResponse})이 <b>같은 판정을 공유</b>하도록 예외 대신 값으로 표현한다 — {@code
 * ParticipationCancellability}·{@code C2cHostQualification} 과 같은 형태다. Wave 2 에서 목록 화면이 상태 집합으로 자체 판정을
 * 시도했다가 정작 대상인 입금 수집중을 빠뜨려 사실과 다른 안내를 띄운 적이 있다(docs/56 §21-4) — 그래서 판정을 서버 한 곳에 둔다.
 *
 * <p>판정 → 표현(에러코드 · 안내 문구) 변환은 호출부에 둔다.
 *
 * <p>⚠️ 이 값은 <b>조회 시점 스냅샷</b>이다. 최종 차단은 취소 CAS({@code
 * BuncheolRepository#hostCancelIfCollectingAndNoConfirmed})가 원자적으로 한다.
 */
public enum BuncheolHostCancellability {
  /** 취소 가능 — 모집중 · 입금확인 0건인 입금 수집중 · 인원미달 자동취소. */
  CANCELLABLE,
  /** 상태상 취소 불가 — 진행확정(CONFIRMED) 이후와 이미 개최자 취소된 분철 (BCH-050). */
  BLOCKED_BY_STATUS,
  /**
   * 입금확인(CONFIRMED)된 참여가 1건 이상인 입금 수집중 분철 — 직거래라 확인된 돈은 이미 개최자 계좌에 있고 플랫폼이 환불을 강제할 수단이 없다
   * (BCH-093). "보냈어요"(PAYMENT_SENT)만 누른 건은 참여자 자기 신고라 <b>포함하지 않는다</b> — 허위 마킹 1건으로 개최자가 분철을 영영
   * 못 접게 되기 때문이다(docs/56 §21-2).
   */
  BLOCKED_BY_CONFIRMED_PAYMENT;

  /**
   * @param status 분철 현재 상태
   * @param confirmedParticipationCount 입금확인(CONFIRMED) 참여 수. {@link #requiresConfirmedCount} 가
   *     false 인 상태에서는 판정에 쓰이지 않으므로 0 을 넘겨도 된다 — 그 구간엔 입금확인이 있을 수 없거나(모집중·자동취소) 이미 상태로
   *     막히기(진행확정) 때문이다.
   */
  public static BuncheolHostCancellability of(
      final BuncheolStatus status, final long confirmedParticipationCount) {
    if (!isCancellableStatus(status)) {
      return BLOCKED_BY_STATUS;
    }
    if (requiresConfirmedCount(status) && confirmedParticipationCount > 0) {
      return BLOCKED_BY_CONFIRMED_PAYMENT;
    }
    return CANCELLABLE;
  }

  /**
   * 이 상태의 판정에 입금확인 건수가 필요한지. 단건 취소 게이트가 필요 없는 상태에서 집계 쿼리를 돌리지 않도록 열어 두되, "어느 상태가 건수를 보는지" 의
   * 지식은 이 enum 안에만 둔다.
   */
  public static boolean requiresConfirmedCount(final BuncheolStatus status) {
    return status == BuncheolStatus.PAYMENT_COLLECTING;
  }

  public boolean isCancellable() {
    return this == CANCELLABLE;
  }

  // 취소 CAS 가 시도하는 상태와 반드시 일치해야 한다 (BuncheolDomainService#cancelBuncheol).
  // 어긋나면 목록이 "취소 가능" 이라 말한 카드가 409 로 떨어지므로, 전 상태를 도는 테스트로 못 박는다.
  private static boolean isCancellableStatus(final BuncheolStatus status) {
    return status == BuncheolStatus.RECRUITING
        || status == BuncheolStatus.PAYMENT_COLLECTING
        || status == BuncheolStatus.CANCELLED;
  }
}
