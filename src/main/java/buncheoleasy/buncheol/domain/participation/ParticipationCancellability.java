package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.buncheol.domain.Buncheol;
import java.util.Set;

/**
 * 참여자 자발 취소 가능 여부 판정 결과 (docs/46 §5 · docs/56 S-1).
 *
 * <p>취소 API 의 게이트({@code ParticipationService#cancelByParticipant})와 참여 조회 응답({@code
 * MyParticipationResponse}·{@code ParticipationDetailResponse})이 <b>같은 판정을 공유</b>하도록 예외 대신 값으로 표현한다 —
 * {@code C2cHostQualification}(docs/53 Q-07)과 같은 형태다. 조회가 자체 판정을 복제하면 가드와 어긋나 "버튼은 있는데 누르면 409" 또는 그
 * 반대(취소 가능한데 버튼이 없음)가 생긴다.
 *
 * <p>판정 → 표현(에러코드 · 안내 문구) 변환은 호출부에 둔다. 이 enum 이 에러코드를 알면 "가능할 때는 호출하면 안 되는" 부분 함수가 생긴다.
 *
 * <p>⚠️ 이 값은 <b>조회 시점 스냅샷</b>이라 최종 차단은 취소 CAS({@code ParticipationRepository#cancelByUserIfCancellable})가
 * 한다. 그 CAS 는 {@link #cancellableStatuses()} 를 공유한다.
 */
public enum ParticipationCancellability {
  /** 자발 취소 가능 — 회원 개최의 신청(APPLIED). 입금 단계가 시작되면 스스로 빠질 수 없다. */
  CANCELLABLE,
  /**
   * 상태상 취소 불가 — 입금 대기 · "보냈어요"(PAYMENT_SENT) · 입금확인(CONFIRMED) · <b>이미 취소됨</b>(CANCELLED). API 는 모두
   * BCH-086 으로 응답하지만, <b>이미 취소된 참여</b>는 문의 대상이 아니고 애초에 취소 버튼을 찾을 화면도 아니다. 화면은 이 값만 보고 안내를 띄우지
   * 말고 취소 완료 카드를 제외해야 한다.
   */
  BLOCKED_BY_STATUS,
  /** 운영진 분철의 입금 대기 참여 — 자발 취소 API 자체가 열려 있지 않고 기한이 지나면 자동 취소된다 (BCH-091). */
  FLOW_NOT_SUPPORTED;

  private static final Set<ParticipationStatus> CANCELLABLE_STATUSES =
      Set.of(ParticipationStatus.APPLIED);

  // 돈이 아직 오가지 않은 상태. 그 밖(보냈어요·입금확인·취소)은 플로우와 무관하게 상태 위반이다.
  private static final Set<ParticipationStatus> BEFORE_PAYMENT_STATUSES =
      Set.of(ParticipationStatus.APPLIED, ParticipationStatus.AWAITING_PAYMENT);

  /**
   * 참여와 그 분철로부터 취소 가능 여부를 판정한다. <b>검사 순서가 곧 계약</b>이다 — 상태 → 플로우 → 회원 개최 상태 순으로, 취소 게이트가
   * 던지는 에러코드 우선순위와 일치시킨다(예: 운영진 분철의 입금확인 참여는 BCH-091 이 아니라 BCH-086 을 받는다).
   *
   * <p>판정에 참여 상태와 분철(플로우)이 모두 필요해 두 엔티티를 함께 받는다. 인자를 원시값으로 풀면 호출부마다 "무엇을 넘길지" 를 다시
   * 조립해야 해 그 조립이 어긋날 수 있다.
   */
  public static ParticipationCancellability of(
      final Participation participation, final Buncheol buncheol) {
    ParticipationStatus status = participation.getStatus();
    // 상태 검사를 플로우 가드보다 먼저 한다 — 운영진 분철이라도 이미 입금확인된 참여는 "기한이 지나면 자동 취소돼요" 가 사실이 아니다.
    if (!BEFORE_PAYMENT_STATUSES.contains(status)) {
      return BLOCKED_BY_STATUS;
    }
    if (buncheol.isStaff()) {
      return FLOW_NOT_SUPPORTED;
    }
    // 회원 개최는 입금 단계가 시작되면(입금 대기) 스스로 빠질 수 없다.
    return CANCELLABLE_STATUSES.contains(status) ? CANCELLABLE : BLOCKED_BY_STATUS;
  }

  public boolean isCancellable() {
    return this == CANCELLABLE;
  }

  /**
   * 자발 취소가 열려 있는 상태(신청). 자발 취소 CAS({@code ParticipationRepository#cancelByUserIfCancellable})가 <b>이 집합을 그대로
   * 써야 한다</b> — 판정과 CAS 가 각자 상태 목록을 들면 한쪽만 바뀌었을 때 "버튼은 보이는데 눌러도 실패"(또는 그 반대)가 생긴다.
   */
  public static Set<ParticipationStatus> cancellableStatuses() {
    return CANCELLABLE_STATUSES;
  }
}
