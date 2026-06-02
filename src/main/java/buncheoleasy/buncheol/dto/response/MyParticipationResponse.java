package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;

public record MyParticipationResponse(
    Long participationId,
    Long buncheolId,
    String buncheolTitle,
    int buncheolMemberCount,
    String memberName,
    long bidAmount,
    long shippingFee,
    long paymentAmount,
    ParticipationStatus participationStatus,
    BuncheolStatus buncheolStatus,
    Instant buncheolDeadline,
    Instant paymentDueAt,
    Integer closedRank) {}
