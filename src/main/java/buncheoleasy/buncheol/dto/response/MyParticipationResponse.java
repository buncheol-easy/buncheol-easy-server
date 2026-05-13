package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.LocalDateTime;

public record MyParticipationResponse(
    Long participationId,
    Long buncheolId,
    String buncheolTitle,
    int buncheolMemberCount,
    String memberName,
    long bidAmount,
    ParticipationStatus participationStatus,
    BuncheolStatus buncheolStatus,
    LocalDateTime buncheolDeadline,
    LocalDateTime paymentDueAt,
    Integer closedRank) {}
