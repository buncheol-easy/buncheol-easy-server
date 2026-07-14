package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;

/** 마이페이지 개최 목록 항목. 썸네일을 함께 내려 프론트가 분철 상세를 추가 조회하지 않아도 되게 한다. */
public record MyHostedBuncheolResponse(
    Long buncheolId,
    String title,
    String groupName,
    BuncheolStatus status,
    Instant deadline,
    int memberSlotCount,
    long activeParticipationCount,
    Instant createdAt,
    String thumbnailUrl) {}
