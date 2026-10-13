package buncheoleasy.buncheol.domain;

import java.time.Instant;

/**
 * 개최자가 분철의 거래 조건({@link BuncheolTerms})을 지금 바꿀 수 있는지 — 판정 결과.
 *
 * <p>분철 수정 API 의 사전 검사({@code BuncheolService#modifyBuncheol})와 상세 응답의 {@code termsEditable} 이 <b>같은 판정을
 * 공유</b>하도록 예외 대신 값으로 둔다({@link BuncheolHostCancellability} 와 같은 형태). 두 곳이 갈리면 화면은 칸을 열어 주는데 저장은
 * 409 가 된다.
 *
 * <p>⚠️ 이 값은 <b>조회 시점 판정</b>이다. 최종 차단은 조건부 UPDATE({@link BuncheolRepository#changeTermsIfNoApplicant})가
 * 원자적으로 한다 — 판정 뒤 저장 사이에 들어온 신청은 그 UPDATE 의 NOT EXISTS 가 막는다.
 */
public enum BuncheolTermsEditability {
  /** 바꿀 수 있다 — 모집중·마감 전인 C2C 분철에 활성 신청이 없다. */
  EDITABLE,
  /** 신청을 받는 구간이 아니다 — 모집중이 아니거나 마감이 지났다 (BCH-060). */
  BLOCKED_BY_STATUS,
  /** 운영진 개최(LEGACY) 분철 — 이벤트 운영 절차가 붙어 있어 이 경로로 열지 않는다 (BCH-084). */
  BLOCKED_BY_FLOW,
  /** 활성 신청(신청·입금 확인 중·보냈어요·확정)이 하나라도 있다 — 그 사람이 본 조건이다 (BCH-124). */
  BLOCKED_BY_APPLICANT;

  /**
   * @param hasActiveParticipation 활성 참여({@code ParticipationStatus#active()})가 하나라도 있는지. 조건부 UPDATE 의
   *     NOT EXISTS 와 같은 상태 집합이어야 한다
   */
  public static BuncheolTermsEditability of(
      final BuncheolStatus status,
      final FlowType flowType,
      final Instant deadline,
      final Instant now,
      final boolean hasActiveParticipation) {
    // ⚠️ switch 식으로 둔다 — 상태·방식이 늘면 컴파일 에러로 결정을 강제한다. == 비교면 새 값이 「수정 가능」으로 새는 fail-open 이
    // 된다(Buncheol#validateOpenChatUrlEditable 과 같은 이유).
    boolean recruiting =
        switch (status) {
          case RECRUITING -> deadline.isAfter(now);
          case PAYMENT_COLLECTING, CONFIRMED, CANCELLED, HOST_CANCELLED -> false;
        };
    if (!recruiting) {
      return BLOCKED_BY_STATUS;
    }
    boolean c2c =
        switch (flowType) {
          case C2C -> true;
          case LEGACY -> false;
        };
    if (!c2c) {
      return BLOCKED_BY_FLOW;
    }
    return hasActiveParticipation ? BLOCKED_BY_APPLICANT : EDITABLE;
  }

  public boolean isEditable() {
    return this == EDITABLE;
  }
}
