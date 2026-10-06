package buncheoleasy.buncheol.application;

import java.util.List;

/**
 * 분철이 취소됨(활성 참여 → CANCELLED). cascade 로 취소된 참여자에게 사유와 함께 분철 취소 알림.
 *
 * <p>참여 단위가 아니라 분철 단위로 취소분 전체를 싣는다 — C2C 1인 다슬롯에서 참여마다 발행하면 같은 사람에게 같은 알림이 슬롯 수만큼 가므로, 리스너가
 * 사람 단위로 합산해 1건씩 보낼 수 있어야 한다 ({@link BuncheolConfirmedEvent} 와 같은 규칙).
 */
public record BuncheolCancelledEvent(
    Long buncheolId, List<Long> participationIds, BuncheolCancelReason reason) {}
