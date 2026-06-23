package buncheoleasy.buncheol.application.participation;

/** 개최자가 입금을 확인함(AWAITING_PAYMENT → CONFIRMED). 참여자에게 참여 확정 알림. */
public record PaymentConfirmedEvent(Long participationId) {}
