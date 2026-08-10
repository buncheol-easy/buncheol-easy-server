package buncheoleasy.buncheol.application;

/**
 * C2C 분철이 성사 확정됨 (RECRUITING → PAYMENT_COLLECTING, docs/46 §4.1). 신청자 전원이 입금 대기(AWAITING_PAYMENT)로
 * 일괄 전이된 직후 발행되며, 커밋 후 성사 확정·입금 안내 알림톡과 인앱 수신함 기록의 트리거가 된다.
 */
public record BuncheolCollectingStartedEvent(Long buncheolId) {}
