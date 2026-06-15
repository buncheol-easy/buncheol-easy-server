package buncheoleasy.buncheol.application;

/** 낙찰자가 '입금 완료'를 신고함(AWAITING_PAYMENT → PAYMENT_REPORTED). 개최자에게 입금 확인 요청 알림. */
public record PaymentReportedEvent(Long participationId) {}
