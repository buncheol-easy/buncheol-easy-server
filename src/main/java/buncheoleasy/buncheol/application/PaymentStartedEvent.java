package buncheoleasy.buncheol.application;

import java.util.List;

/**
 * 회원 개최 분철의 입금 단계가 시작됨 (RECRUITING → PAYMENT_COLLECTING). 마감 시각, 마지막 열린 자리 신청, 자리 빼기로 정원이 찬 순간
 * 모두 {@link PaymentStarter} 가 발행하며, 커밋 후 참여자 입금 안내·개최자 알림과 수신함 기록의 트리거가 된다.
 *
 * <p>{@code participationIds} 는 커밋 시점에 입금 대기가 된 참여의 스냅샷이다 — 리스너가 실행 시점에 다시 고르지 않는다.
 */
public record PaymentStartedEvent(Long buncheolId, List<Long> participationIds) {}
