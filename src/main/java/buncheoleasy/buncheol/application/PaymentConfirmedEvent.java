package buncheoleasy.buncheol.application;

/** 개최자가 입금을 수동 확인함(PAYMENT_REPORTED → CONFIRMED). 참여자에게 입금 확인 알림. */
public record PaymentConfirmedEvent(Long participationId) {}
