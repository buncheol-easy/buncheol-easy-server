package buncheoleasy.buncheol.application;

import java.util.List;

/**
 * 분철이 진행확정됨(LEGACY 는 마감 시점 입금확인 인원 ≥ 최소 인원, C2C 는 전원 입금확인). 입금확인된 참여자에게 진행확정 알림.
 *
 * <p>참여 단위가 아니라 분철 단위로 전이분 전체를 싣는다 — C2C 1인 다슬롯에서 참여마다 발행하면 같은 사람에게 같은 알림이 슬롯 수만큼 가므로, 리스너가 유저
 * 단위로 합산해 1건씩 보낼 수 있어야 한다 (docs/46 §4.7-A3 의 성사 안내와 같은 규칙).
 */
public record BuncheolConfirmedEvent(Long buncheolId, List<Long> participationIds) {}
