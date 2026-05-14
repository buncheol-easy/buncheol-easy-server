package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;

public record MyHostedBuncheolResponse(
    Long buncheolId,
    String title,
    String groupName,
    BuncheolStatus status,
    Instant deadline,
    int memberSlotCount,
    long activeParticipationCount,
    Instant createdAt) {}
