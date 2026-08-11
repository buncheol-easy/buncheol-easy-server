package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import java.time.Instant;

/**
 * 마이페이지 개최 목록 항목. 썸네일을 함께 내려 프론트가 분철 상세를 추가 조회하지 않아도 되게 한다.
 *
 * <p>{@code flowType} 은 검색 목록(`GET /v1/buncheols`)에만 있고 여기엔 빠져 있어 FE 가 상태로 플로우를 추정하던 것을 바로잡은 값이다
 * (docs/53 Q-13).
 */
public record MyHostedBuncheolResponse(
    Long buncheolId,
    String title,
    String groupName,
    BuncheolStatus status,
    Instant deadline,
    int memberSlotCount,
    long activeParticipationCount,
    Instant createdAt,
    String thumbnailUrl,
    FlowType flowType) {}
