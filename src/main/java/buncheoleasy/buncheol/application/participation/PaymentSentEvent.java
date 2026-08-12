package buncheoleasy.buncheol.application.participation;

/** C2C 참여자가 '보냈어요' 를 마킹함(AWAITING_PAYMENT → PAYMENT_SENT). 커밋 후 개최자에게 입금 확인 요청 알림톡 발송을 트리거한다. */
public record PaymentSentEvent(Long participationId) {}
