package buncheoleasy.buncheol.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 분철이 취소된 사유. {@link BuncheolCancelledEvent} 로 실려 참여자 취소 알림의 사유 문구로 표시된다. */
@Getter
@RequiredArgsConstructor
public enum BuncheolCancelReason {
  // 개최자가 분철을 직접 취소.
  HOST_CANCELLED("개최자 취소"),
  // 마감 시점 인원이 최소 진행 인원에 미달(운영진 분철은 입금확인 인원, 회원 개최는 신청 인원).
  MIN_HEADCOUNT_NOT_MET("최소 진행 인원 미달");

  private final String description;
}
