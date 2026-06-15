package buncheoleasy.buncheol.application;

/** 마감 또는 차순위 이양으로 참여가 낙찰됨(ACTIVE_BID → AWAITING_PAYMENT). 참여자에게 낙찰·입금 안내 알림. */
public record ParticipationWonEvent(Long participationId) {}
