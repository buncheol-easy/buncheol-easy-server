package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

/** 참여자 본인의 참여 상세. 입금확인중(AWAITING_PAYMENT) 일 때만 개최자 계좌를 노출한다. */
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
    HostAccountResponse hostAccount) {}
