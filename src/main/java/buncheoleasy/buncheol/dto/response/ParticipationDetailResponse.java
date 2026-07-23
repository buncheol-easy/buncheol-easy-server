package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

/**
 * 참여자 본인의 참여 상세. 입금확인중(AWAITING_PAYMENT) 일 때만 개최자 계좌를 노출한다. {@code payback} 은 오픈 이벤트 배송비
 * 환급(배송비 돌려받기) 상태로, 비대상이어도 status=NONE 으로 항상 내려준다.
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
    ShippingFeePaybackResponse payback) {}
