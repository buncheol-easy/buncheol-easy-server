package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;

/**
 * 개최자의 묶음 「제외」 가능 여부. 「제외」 API 의 게이트와 개최 관리 응답이 <b>같은 판정을 공유</b>하도록 예외 대신 값으로 둔다 —
 * 둘이 갈리면 "버튼은 있는데 누르면 409" 가 생긴다.
 *
 * <p>「제외」는 진행 확정 뒤 수락한 추가 참여가 입금 기한(수락 + 24시간)이 지나도록 입금 확인이 안 됐을 때 그 묶음을 빼는 출구다.
 * 입금 단계(진행 확정 전)에는 없다 — 그때 미입금 참여는 진행 확정이 함께 뺀다.
 *
 * <ul>
 *   <li><b>기한 전</b>은 닫는다. 이체가 늦게 찍혀 정상 입금자를 빼는 사고가 나면 복구 경로가 문의뿐이다.
 *   <li><b>입금 확인된 슬롯</b>이 있으면 닫는다. 확정분은 파기와 환불 경로로만 끝난다.
 *   <li>「보냈어요」를 누른 묶음도 기한이 지나면 뺄 수 있다 — 개최자가 통장을 보고 판단한다.
 * </ul>
 *
 * <p>⚠️ 조회 시점 판정이라 최종 차단은 「제외」 CAS(기한 조건)와 묶음 참여 잠금이 한다.
 */
public enum BundleReleasability {
  /** 제외 가능 — 진행 확정 뒤 수락한 묶음이 기한이 지났고 확정 슬롯이 없다. */
  RELEASABLE,
  /** 분철이 진행 확정 전이다(또는 취소됨). 「제외」는 진행 확정 뒤에만 있다. */
  BUNCHEOL_NOT_CONFIRMED,
  /** 수락 전 추가 신청이다. 수락하거나 거절한다. */
  NOT_ACCEPTED,
  /** 입금 기한 전. 기한이 지나야 열린다. */
  BEFORE_DUE,
  /** 입금 확인된 슬롯이 있다. */
  HAS_CONFIRMED,
  /** 살아 있는 슬롯이 없다(이미 제외·취소됨). */
  ALREADY_CLOSED;

  public boolean isReleasable() {
    return this == RELEASABLE;
  }

  /**
   * 묶음과 그 슬롯들로부터 「제외」 가능 여부를 판정한다.
   *
   * <p><b>검사 순서가 곧 계약이다.</b> 확정 슬롯이 있으면서 기한도 안 된 묶음은 "기한을 기다리세요" 가 아니라 "확정분은 뺄 수 없어요"
   * 라고 답한다. 살아 있는 슬롯을 묶음의 {@code closedAt}·{@code dueAt} 보다 먼저 보는 이유 — 슬롯은 잠금 조회로 읽은 최신 값이고
   * 묶음 엔티티는 잠금 전 일반 조회라 낡았을 수 있다.
   *
   * @param slots 그 묶음의 <b>모든</b> 슬롯 (취소분 포함). 일부만 넘기면 확정 슬롯을 놓칠 수 있다
   */
  public static BundleReleasability of(
      final BuncheolStatus buncheolStatus,
      final ParticipationBundle bundle,
      final Iterable<Participation> slots,
      final Instant now) {
    if (buncheolStatus != BuncheolStatus.CONFIRMED) {
      return BUNCHEOL_NOT_CONFIRMED;
    }
    boolean anyActive = false;
    boolean anyAccepted = false;
    for (Participation slot : slots) {
      ParticipationStatus status = slot.getStatus();
      if (status == ParticipationStatus.CONFIRMED) {
        return HAS_CONFIRMED;
      }
      if (ParticipationStatus.active().contains(status)) {
        anyActive = true;
      }
      if (ParticipationStatus.payableStatuses().contains(status)) {
        anyAccepted = true;
      }
    }
    if (!anyActive || bundle.getClosedAt() != null) {
      return ALREADY_CLOSED;
    }
    if (!anyAccepted) {
      return NOT_ACCEPTED;
    }
    // 기한이 비어 있으면 거부한다(fail-closed).
    if (bundle.getDueAt() == null || bundle.getDueAt().isAfter(now)) {
      return BEFORE_DUE;
    }
    return RELEASABLE;
  }
}
