package buncheoleasy.buncheol.dto.response;

import java.time.Instant;
import java.util.List;

/** 분철 공개 목록 조회 응답의 단일 카드. */
public record BuncheolSummaryResponse(
    Long id,
    String title,
    Instant deadline,
    boolean bookmarked,
    String groupName,
    List<String> memberNames) {}
