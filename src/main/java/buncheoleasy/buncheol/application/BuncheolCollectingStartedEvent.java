package buncheoleasy.buncheol.application;

import java.util.List;

/**
 * C2C 분철이 성사 확정됨 (RECRUITING → PAYMENT_COLLECTING, docs/46 §4.1). 신청자 전원이 입금 대기(AWAITING_PAYMENT)로
 * 일괄 전이된 직후 발행되며, 커밋 후 성사 확정·입금 안내 알림톡과 인앱 수신함 기록의 트리거가 된다.
 *
 * <p>{@code participationIds} 는 커밋 시점 전이 대상 스냅샷이다 — 리스너가 실행 시점 상태를 재조회하면 그 사이 들어온 추가 모집
 * 참여(개별 안내를 이미 받음)가 섞여 중복 발송된다.
 */
public record BuncheolCollectingStartedEvent(Long buncheolId, List<Long> participationIds) {}
