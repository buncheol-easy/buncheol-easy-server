package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

/**
 * 운영자(개최자) 관리 화면의 참여자 1건. 활성 참여 전체를 노출하며, 환불 계좌와(확정 시) 배송 스냅샷을 포함한다. {@code paymentSentAt} 은
 * C2C "보냈어요" 마킹 시각 — 개최자가 통장 대조 우선순위를 잡는 근거다(환불 계좌 예금주 = 입금자명 대조 키).
 */
public record BuncheolManagementParticipantResponse(
    Long participationId,
    String participantNickname,
    Long buncheolMemberId,
    String memberName,
    long amount,
    ParticipationStatus status,
    Instant dueAt,
    Instant confirmedAt,
    RefundAccountResponse refundAccount,
    ManagementDeliveryResponse delivery,
    Instant paymentSentAt) {}
