package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.List;

/**
 * 분철 공개 목록 조회 응답의 단일 카드.
 *
 * <p>{@code status} 는 모집중/마감 그룹을 클라이언트가 구분(마감 배지·섹션)하도록 노출한다. 목록은 CANCELLED 를 제외하므로 {@code RECRUITING}
 * 또는 {@code CONFIRMED} 만 내려간다.
 */
public record BuncheolSummaryResponse(
    Long id,
    String title,
    BuncheolStatus status,
    Instant deadline,
    boolean bookmarked,
    String groupName,
    String thumbnailUrl,
    List<String> memberNames) {}
