package buncheoleasy.buncheol.application;

/** 분철이 진행확정됨(마감 시점 입금확인 인원 ≥ 최소 인원). 입금확인된 참여자에게 진행확정 알림. */
public record BuncheolConfirmedEvent(Long participationId) {}
