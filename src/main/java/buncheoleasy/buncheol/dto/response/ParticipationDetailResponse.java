package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

/**
 * 참여자 본인의 참여 상세. 입금 대기(AWAITING_PAYMENT·PAYMENT_SENT) 일 때만 개최자 계좌를 노출한다 — C2C 는 확정 시점 스냅샷 계좌
 * (docs/46 §3-5·§4.7-B1). {@code payback} 은 오픈 이벤트 배송비 환급(배송비 돌려받기) 상태로, 비대상이어도 status=NONE 으로 항상
 * 내려준다. {@code openChatUrl} 은 C2C 개최자 소통 채널(없으면 null).
 */
public record ParticipationDetailResponse(
    Long participationId,
    Long buncheolId,
    String buncheolTitle,
    String memberName,
    long amount,
    ParticipationStatus status,
    ParticipationCancelReason cancelReason,
    Instant dueAt,
    Instant confirmedAt,
    HostAccountResponse hostAccount,
    ShippingFeePaybackResponse payback,
    FlowType flowType,
    Instant paymentSentAt,
    // 개최자 반려 시각. 값이 있고 status=AWAITING_PAYMENT 면 "입금 확인 안 됨 · 재확인 필요" 상태다 (docs/53 Q-03).
    // 참여자가 다시 "보냈어요" 를 누르면 null 로 초기화된다.
    Instant paymentRejectedAt,
    String openChatUrl) {}
