package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

/**
 * 운영자(개최자) 관리 화면의 참여자 1건. 활성 참여 전체를 노출하며, 환불 계좌와(확정 시) 배송 스냅샷을 포함한다. {@code paymentSentAt} 은
 * C2C "보냈어요" 마킹 시각 — 개최자가 통장 대조 우선순위를 잡는 근거다(환불 계좌 예금주 = 입금자명 대조 키).
 *
 * <p>{@code amount} 는 <b>배송비를 포함한 입금 총액</b>이고 {@code shippingFee} 는 그중 배송비다. 다슬롯은 배송비가 묶음 첫 슬롯에만
 * 붙어(같은 분철의 두 번째 슬롯은 0) 참여자별 총액이 서로 다른데, 합계만 보면 개최자가 그 차이를 설명할 수 없다 (docs/53 Q-22).
 */
public record BuncheolManagementParticipantResponse(
    Long participationId,
    String participantNickname,
    Long buncheolMemberId,
    String memberName,
    long amount,
    long shippingFee,
    ParticipationStatus status,
    Instant dueAt,
    Instant confirmedAt,
    RefundAccountResponse refundAccount,
    ManagementDeliveryResponse delivery,
    Instant paymentSentAt) {}
