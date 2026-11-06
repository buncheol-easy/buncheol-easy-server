package buncheoleasy.buncheol.application;

/**
 * 회원 개최 분철이 마감 때 최소 진행 멤버 수에 못 미쳐 자동 취소됨 — 개최자 알림 전용. 참여자 취소 안내({@link
 * BuncheolCancelledEvent})는 취소된 참여가 0 이면 나가지 않지만, 개최자는 신청이 0명이어도 마감된 것을 알아야 해서 따로 낸다.
 *
 * @param appliedSlotCount 마감 판정 때 취소된 신청 자리 수(0 가능). 분철 행 잠금 아래에서 전이된 수라 실제 취소 수와 같다
 */
public record BuncheolAutoCancelledEvent(Long buncheolId, int appliedSlotCount) {}
