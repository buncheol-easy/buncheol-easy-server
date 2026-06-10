package buncheoleasy.buncheol.application;

/** 입금 기한이 임박함(AWAITING_PAYMENT, 마감 3시간 전). 참여자에게 입금 독려 알림. */
public record PaymentDueImminentEvent(Long participationId) {}
