package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.LocalDateTime;

public record MyHostedBuncheolResponse(
    Long buncheolId,
    String title,
    String groupName,
    BuncheolStatus status,
    LocalDateTime deadline,
    int memberSlotCount,
    long activeParticipationCount,
    LocalDateTime createdAt) {}
