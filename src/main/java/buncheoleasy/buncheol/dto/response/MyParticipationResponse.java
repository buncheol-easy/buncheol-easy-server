package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

public record MyParticipationResponse(
    Long participationId,
    Long buncheolId,
    String buncheolTitle,
    int buncheolMemberCount,
    String memberName,
    long amount,
    ParticipationStatus participationStatus,
    ParticipationCancelReason cancelReason,
    BuncheolStatus buncheolStatus,
    Instant buncheolDeadline,
    Instant dueAt,
    Instant confirmedAt) {}
