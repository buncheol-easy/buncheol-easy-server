package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

/**
 * 운영자 관리 화면의 참여자 1건. 입금확인 대상(AWAITING_PAYMENT)·확정 참여(CONFIRMED)를 모두 노출하며, 환불 계좌와(확정 시) 배송 스냅샷을 포함한다.
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
    ManagementDeliveryResponse delivery) {}
