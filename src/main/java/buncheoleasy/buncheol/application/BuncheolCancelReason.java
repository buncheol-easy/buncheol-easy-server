package buncheoleasy.buncheol.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 분철이 취소된 사유. {@link BuncheolCancelledEvent} 로 실려 참여자 취소 알림의 사유 문구로 표시된다. */
@Getter
@RequiredArgsConstructor
public enum BuncheolCancelReason {
  // 개최자가 분철을 직접 취소.
  HOST_CANCELLED("개최자 취소"),
  // 마감 시점에 입금확인된 인원이 최소 진행 인원에 미달.
  MIN_HEADCOUNT_NOT_MET("최소 진행 인원 미달"),
  // C2C: 신청 마감 후 확정 유예(48h) 안에 개최자가 성사 확정을 하지 않아 자동 미성사 취소 (docs/46 §7.1-5).
  NOT_FINALIZED("개최자 미확정 마감");

  private final String description;
}
