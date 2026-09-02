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
 * <p>슬롯 단위 API 를 남겨 둔 탓에 <b>부분 마킹</b>이 생길 수 있다(구 클라가 A 를 먼저 마킹하고, 신 클라가 B 를
 * 묶음으로 마킹). 이번 호출분만 합산하면 개최자가 <b>실제 이체액보다 작은 금액</b>으로 통장을 대조하게 되고, 그게
 * 곧 반려로 이어져 이 기능이 없애려던 문제가 형태만 바꿔 남는다.
 *
 * <p>중복 발송 방지는 "CAS 가 0행이면 이벤트를 발행하지 않는다" 가 따로 맡으므로 두 관심사를 나눠도 안전하다.
 */
public record BundlePaymentSentEvent(
    Long bundleId, List<Long> markedParticipationIds, List<Long> sentParticipationIds) {}
