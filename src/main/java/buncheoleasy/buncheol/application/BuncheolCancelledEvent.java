package buncheoleasy.buncheol.application;

/** 분철이 취소됨(활성 참여 → CANCELLED). 참여자에게 사유와 함께 분철 취소 알림. */
public record BuncheolCancelledEvent(Long participationId, BuncheolCancelReason reason) {}
