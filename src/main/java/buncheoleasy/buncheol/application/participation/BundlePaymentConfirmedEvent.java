package buncheoleasy.buncheol.application.participation;

import java.util.List;

/**
 * 개최자가 묶음의 입금을 한 번에 확인했다. 참여자에게 <b>묶음 1통</b>으로 알린다 — 이체가 1회였으므로 확인도 1회다.
 *
 * <p>배송 스냅샷 생성과 분철 진행확정 판정도 이 이벤트를 받은 뒤가 아니라 <b>같은 트랜잭션 안에서</b> 처리된다 —
 * 커밋 전에 끝나야 확정 직후 조회가 일관된다.
 */
public record BundlePaymentConfirmedEvent(Long bundleId, List<Long> confirmedParticipationIds) {}
