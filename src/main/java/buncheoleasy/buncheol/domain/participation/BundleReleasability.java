package buncheoleasy.buncheol.domain.participation;

import java.time.Instant;

/**
 * 개최자의 묶음 「제외」 가능 여부 판정 결과 (docs/70 결정 8·9 · docs/71 §8-1).
 *
 * <p>제출 시점 게이트와 개최 관리 화면의 사전 조회가 <b>같은 판정을 공유</b>하도록 예외 대신 값으로 표현한다 — {@link
 * ParticipationCancellability} 와 같은 형태다. 조회가 자체 판정을 복제하면 "버튼은 있는데 누르면 409" 또는 그 반대가 생긴다.
 *
 * <p><b>「제외」는 미입금자를 정리하는 도구지 사람을 고르는 도구가 아니다.</b> 그래서 가드가 한 줄이다 —
 * <b>입금 기한이 정해졌고 그 기한이 지났을 때만</b> 열린다. 상태로 갈라 보지 않는다.
 *
 * <ul>
 *   <li><b>모집 중</b>(기한 없음)은 닫는다. 참여자 입장에서 <b>아직 확정도 안 됐는데 갑자기 빠지면 자기가 뭘 잘못했나
 *       싶어진다</b> — 개최자가 임의로 사람을 고를 수 있는 도구가 되면 안 된다 (2026-08-31 사용자 결정).
 *   <li><b>기한 전</b>도 닫는다. 이체가 늦게 찍혀 <b>정상 입금자를 빼는 사고</b>가 나면 복구 경로가 문의뿐이다.
 *       반대 방향 리스크(허위 마킹)는 하루 늦게 처리될 뿐이라 막는 쪽 손해가 작다.
 *   <li><b>입금확인된 슬롯</b>이 하나라도 있으면 닫는다. 확정분은 분철 취소 cascade + 환불 경로로만 끝난다.
 * </ul>
 *
 * <p>🟢 기한이 {@code null} 이면 <b>거부</b>이므로 이 판정은 fail-closed 다 — 배포선 창에서 기한이 안 채워진 묶음이
 * 있어도 안전한 쪽으로 닫힌다.
 *
 * <p>⚠️ 이 값은 <b>조회 시점 스냅샷</b>이라 최종 차단은 「제외」 API 의 CAS 가 한다.
 */
public enum BundleReleasability {
  /** 제외 가능 — 입금 기한이 지났고 확정 슬롯이 없다. */
  RELEASABLE,
  /** 모집 중이라 입금 기한 자체가 없다. 개최자가 임의로 참여자를 자를 수 없다. */
  RECRUITING,
  /** 입금 기한 전. 기한이 지나야 열린다. */
  BEFORE_DUE,
  /** 입금확인된 슬롯이 있다. 확정분은 분철 취소 + 환불 경로로만 끝난다. */
  HAS_CONFIRMED,
  /** 이미 끝난 묶음(전원 취소·확정 종료). */
  ALREADY_CLOSED;

  public boolean isReleasable() {
    return this == RELEASABLE;
  }

  /**
   * 묶음과 그 슬롯들로부터 제외 가능 여부를 판정한다.
   *
   * <p><b>검사 순서가 곧 계약이다</b> — 종료 → 확정 → 기한 순으로, 개최자에게 가장 먼저 알려야 할 사유가 앞선다. 예컨대
   * 확정 슬롯이 있으면서 기한도 안 된 묶음은 "기한을 기다리세요" 가 아니라 "확정분은 뺄 수 없어요" 라고 답해야 한다.
   *
   * @param slots 그 묶음의 <b>모든</b> 슬롯 (취소분 포함). 일부만 넘기면 확정 슬롯을 놓칠 수 있다
   */
  public static BundleReleasability of(
      final ParticipationBundle bundle,
      final Iterable<Participation> slots,
      final Instant now) {
    if (bundle.getClosedAt() != null) {
      return ALREADY_CLOSED;
    }
    for (Participation slot : slots) {
      if (slot.getStatus() == ParticipationStatus.CONFIRMED) {
        return HAS_CONFIRMED;
      }
    }
    if (bundle.getDueAt() == null) {
      return RECRUITING;
    }
    return bundle.getDueAt().isAfter(now) ? BEFORE_DUE : RELEASABLE;
  }
}
