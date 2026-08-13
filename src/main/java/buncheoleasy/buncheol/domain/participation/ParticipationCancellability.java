package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.buncheol.domain.Buncheol;

/**
 * 참여자 자발 취소 가능 여부 판정 결과 (docs/46 §5 · docs/56 H-09 · S-1).
 *
 * <p>취소 API 의 게이트({@code ParticipationService#cancelByParticipant})와 참여 조회 응답({@code
 * MyParticipationResponse}·{@code ParticipationDetailResponse})이 <b>같은 판정을 공유</b>하도록 예외 대신 값으로 표현한다 —
 * {@code C2cHostQualification}(docs/53 Q-07)과 같은 형태다. 조회가 자체 판정을 복제하면 가드와 어긋나 "버튼은 있는데 누르면 409" 또는 그
 * 반대(취소 가능한데 버튼이 없음)가 생긴다.
 *
 * <p>판정 → 표현(에러코드 · 안내 문구) 변환은 호출부에 둔다. 이 enum 이 에러코드를 알면 "가능할 때는 호출하면 안 되는" 부분 함수가 생긴다.
 *
 * <p>⚠️ 이 값은 <b>조회 시점 스냅샷</b>이다. 최종 차단은 여전히 취소 API 의 게이트와 CAS 가 한다 — 개최자가 조회 직후 성사 확정을 눌렀다면 응답은
 * {@link #CANCELLABLE} 이었어도 실제 취소는 거부된다.
 */
public enum ParticipationCancellability {
  /** 자발 취소 가능 — docs/46 §5 구간 ①(신청)과 확정을 거치지 않은 ②(입금 대기). */
  CANCELLABLE,
  /**
   * 상태상 취소 불가 — "보냈어요"(PAYMENT_SENT) · 입금확인(CONFIRMED) · 이미 취소됨. 돈이 이미 개최자에게 간 구간이라 고객센터 문의로
   * 안내한다 (BCH-086).
   */
  BLOCKED_BY_STATUS,
  /** LEGACY 참여 — C2C 전용 자발 취소 API 자체가 열려 있지 않다 (BCH-091). */
  FLOW_NOT_SUPPORTED,
  /**
   * 개최자 성사 확정을 거친 입금 대기 — 개최자가 인원을 계산한 뒤라 직접 취소를 막고 개최자 연락을 유도한다 (BCH-092). 추가 모집(docs/46
   * §4.7-E1)으로 확정 <b>이후</b> 생성된 입금 대기 참여는 여기 해당하지 않는다.
   */
  BLOCKED_BY_HOST_CONFIRM;

  /**
   * 참여와 그 분철로부터 취소 가능 여부를 판정한다. <b>검사 순서가 곧 계약</b>이다 — 상태 → 플로우 → 성사 확정 순으로, 취소 게이트가 던지는 에러코드
   * 우선순위와 일치시킨다(예: LEGACY 의 입금확인 참여는 BCH-091 이 아니라 BCH-086 을 받는다).
   *
   * <p>판정에 참여 상태와 분철(플로우 · 성사 확정 시각)이 모두 필요해 두 엔티티를 함께 받는다. 인자를 원시값으로 풀면 호출부마다 "무엇을 넘길지" 를 다시
   * 조립해야 해 그 조립이 어긋날 수 있다.
   */
  public static ParticipationCancellability of(
      final Participation participation, final Buncheol buncheol) {
    // 상태 검사를 플로우 가드보다 먼저 한다 — LEGACY 라도 이미 입금확인된 참여는 "기한이 지나면 자동 취소돼요" 가 사실이 아니다.
    if (!isCancellableStatus(participation.getStatus())) {
      return BLOCKED_BY_STATUS;
    }
    if (!buncheol.isC2c()) {
      return FLOW_NOT_SUPPORTED;
    }
    // 신청(APPLIED)은 확정 전 구간이라 항상 열려 있고, 입금 대기는 성사 확정 일괄 전이로 들어온 참여만 잠근다.
    if (participation.getStatus() == ParticipationStatus.AWAITING_PAYMENT
        && buncheol.isCreatedBeforeFinalize(participation.getCreatedAt())) {
      return BLOCKED_BY_HOST_CONFIRM;
    }
    return CANCELLABLE;
  }

  public boolean isCancellable() {
    return this == CANCELLABLE;
  }

  // 자발 취소가 열려 있는 구간 (docs/46 §5 구간 ①·②). CAS 가 최종 판정이지만, 사유를 고르려면 그 전에 상태를 알아야 한다.
  private static boolean isCancellableStatus(final ParticipationStatus status) {
    return status == ParticipationStatus.APPLIED
        || status == ParticipationStatus.AWAITING_PAYMENT;
  }
}
