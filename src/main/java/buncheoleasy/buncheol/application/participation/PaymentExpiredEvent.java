package buncheoleasy.buncheol.application.participation;

/**
 * 입금 기한(dueAt) 이 지나 참여가 자동 취소됨(AWAITING_PAYMENT → CANCELLED, PAYMENT_TIMEOUT). 입금 만료 스케줄러가 CAS 에
 * 실제로 성공했을 때만 단독 발행한다. 참여자에게 자동 취소·환불 안내 알림.
 */
public record PaymentExpiredEvent(Long participationId) {}
