package buncheoleasy.buncheol.application.participation;

import java.util.List;

/**
 * 참여자가 묶음을 한 번에 「보냈어요」로 표시했다. 개최자에게 <b>묶음 1통</b>으로 알린다 — 이체가 1회이므로 슬롯마다
 * 보내면 개최자가 같은 입금을 여러 건으로 착각한다.
 *
 * @param markedParticipationIds 실제로 마킹된 슬롯 id (멤버명 나열·금액 합산에 쓴다)
 */
public record BundlePaymentSentEvent(Long bundleId, List<Long> markedParticipationIds) {}
