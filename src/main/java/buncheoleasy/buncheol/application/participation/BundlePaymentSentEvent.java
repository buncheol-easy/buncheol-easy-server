package buncheoleasy.buncheol.application.participation;

import java.util.List;

/**
 * 참여자가 묶음을 한 번에 「보냈어요」로 표시했다. 개최자에게 <b>묶음 1통</b>으로 알린다 — 이체가 1회이므로 슬롯마다
 * 보내면 개최자가 같은 입금을 여러 건으로 착각한다.
 *
 * <p>🔴 <b>두 목록이 다르고, 알림은 뒤쪽을 써야 한다.</b>
 *
 * <ul>
 *   <li>{@code markedParticipationIds} — <b>이번 호출이</b> 마킹한 슬롯. 화면이 사후 대조하는 대상이다.
 *   <li>{@code sentParticipationIds} — <b>묶음 전체의</b> 마킹된 슬롯. 알림의 멤버명·금액은 이걸로 만든다.
 * </ul>
 *
 * <p>알림을 묶음 전체로 만드는 이유 — 개최자는 이 금액으로 이체 1건을 통장과 대조한다. 묶음 안 슬롯은 한 번에
 * 마킹돼 지금은 두 목록이 같지만, 갈린다면 이번 호출분만 합산한 금액은 <b>실제 이체액보다 작아</b> 대조가 어긋난다.
 *
 * <p>중복 발송 방지는 "CAS 가 0행이면 이벤트를 발행하지 않는다" 가 따로 맡으므로 두 관심사를 나눠도 안전하다.
 */
public record BundlePaymentSentEvent(
    Long bundleId, List<Long> markedParticipationIds, List<Long> sentParticipationIds) {}
