package buncheoleasy.buncheol.application;

/**
 * C2C 모집 정원 충족 — 신청으로 전 멤버 슬롯이 활성 참여로 채워짐. 커밋 후 개최자에게 진행 확정 독촉 알림톡 발송을 트리거한다. {@code
 * appliedCount} 는 충족 판정 시점의 활성 참여 수(= 슬롯 수) 스냅샷이다.
 */
public record BuncheolFullEvent(Long buncheolId, long appliedCount) {}
