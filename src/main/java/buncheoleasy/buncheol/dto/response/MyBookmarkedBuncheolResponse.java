package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;

public record MyBookmarkedBuncheolResponse(
    Long bookmarkId,
    Long buncheolId,
    String title,
    BuncheolStatus status,
    Instant deadline,
    String groupName,
    String thumbnailUrl) {}
