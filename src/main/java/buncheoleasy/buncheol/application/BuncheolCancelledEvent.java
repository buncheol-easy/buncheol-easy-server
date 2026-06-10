package buncheoleasy.buncheol.application;

/** 개최자가 분철을 취소함(활성 참여 ACTIVE_BID → CANCELLED). 참여자에게 분철 취소 알림. */
public record BuncheolCancelledEvent(Long participationId) {}
