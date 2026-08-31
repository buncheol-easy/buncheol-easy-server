package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

/**
 * 운영자(개최자) 관리 화면의 참여자 1건. 활성 참여 전체를 노출하며, (확정 시) 배송 스냅샷을 포함한다. {@code paymentSentAt} 은
 * C2C "보냈어요" 마킹 시각 — 개최자가 통장 대조 우선순위를 잡는 근거다.
 *
 * <p>{@code bundleId} 는 이 참여가 속한 묶음이고 {@code participantId} 는 그 묶음의 주인이다. 개최자 화면이 슬롯을
 * <b>묶음 1행</b>으로 접고 묶음 단위 조작(「제외」 등)을 부르려면 둘 다 필요하다 — 묶음 id 만으로는 같은 사람의 여러
 * 묶음(추가 모집)을 한 사람 아래로 모을 수 없다. 미연결 참여는 {@code bundleId} 가 {@code null} 일 수 있다.
 *
 * <p><b>계좌 노출 범위</b>(docs/70 결정 21) — 개최자가 통장을 대조하는 데 필요한 것은 <b>입금자명뿐</b>이므로 평시에는 {@code
 * depositorName} 만 내리고 {@code refundAccount} 는 {@code null} 이다. 계좌번호는 <b>개최자가 실제로 환불해야 하는 건</b>, 즉
 * 취소분 중 입금 흔적(마킹·입금확인)이 있는 건에만 채운다 — 직거래라 환불 주체가 개최자다.
 *
 * <p>{@code amount} 는 <b>배송비를 포함한 입금 총액</b>이고 {@code shippingFee} 는 그중 배송비다. 배송비는 <b>묶음당 1회</b>라
 * 같은 묶음의 두 번째 슬롯은 0 이고, 참여자별 총액이 서로 달라진다 — 합계만 보면 개최자가 그 차이를 설명할 수 없다 (docs/53 Q-22).
 * ⚠️ 성사 확정 후 추가 모집은 <b>새 묶음</b>이라 같은 사람의 슬롯 두 개가 모두 >0 일 수 있다.
 */
public record BuncheolManagementParticipantResponse(
    Long participationId,
    Long bundleId,
    Long participantId,
    String participantNickname,
    Long buncheolMemberId,
    String memberName,
    String depositorName,
    long amount,
    long shippingFee,
    ParticipationStatus status,
    Instant dueAt,
    Instant confirmedAt,
    RefundAccountResponse refundAccount,
    ManagementDeliveryResponse delivery,
    Instant paymentSentAt) {}
