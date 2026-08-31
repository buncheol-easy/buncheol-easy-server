package buncheoleasy.buncheol.application.participation;

import java.util.List;

/**
 * 개최자가 묶음을 「제외」했다. 참여자에게 <b>묶음 1통</b>으로 알린다 — 슬롯마다 보내면 같은 사람이 같은 내용을 여러 번 받는다.
 *
 * @param releasedParticipationIds 실제로 취소된 슬롯 id. 알림 조립은 첫 슬롯으로 하고 멤버명은 전건을 나열한다
 */
public record BundleReleasedEvent(Long bundleId, List<Long> releasedParticipationIds) {}
