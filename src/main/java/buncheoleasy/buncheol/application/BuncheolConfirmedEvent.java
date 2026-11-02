package buncheoleasy.buncheol.application;

import java.util.List;

/**
 * 참여가 진행 확정 안내 대상이 됨. LEGACY 는 마감 시점 입금확인 인원 ≥ 최소 인원, C2C 는 개최자의 진행 확정(분철 단위로 전이분 전체)과
 * <b>진행 확정 뒤 추가 묶음 입금 확인(그 묶음 참여만, 묶음마다 1번)</b>이다. 분철당 1번이라고 가정하지 않는다.
 *
 * <p>참여 단위가 아니라 전이분을 한 번에 싣는다 — C2C 1인 다슬롯에서 참여마다 발행하면 같은 사람에게 같은 알림이 슬롯 수만큼 가므로, 리스너가 유저
 * 단위로 합산해 1건씩 보낼 수 있어야 한다 (입금 안내와 같은 규칙).
 */
public record BuncheolConfirmedEvent(Long buncheolId, List<Long> participationIds) {}
