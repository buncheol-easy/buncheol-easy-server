package buncheoleasy.buncheol.application.participation;

/**
 * C2C 개최자가 "보냈어요" 를 반려함 (PAYMENT_SENT → AWAITING_PAYMENT 복귀 + 기한 연장, docs/46 §4.5). 커밋 후 참여자에게
 * 입금 재확인 알림톡(연장된 새 기한 포함)과 인앱 수신함 기록의 트리거가 된다.
 */
public record PaymentRecheckRequestedEvent(Long participationId) {}
