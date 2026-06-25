package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.List;

/**
 * 분철 공개 목록 조회 응답의 단일 카드.
 *
 * <p>{@code status} 는 모집중/진행확정/취소 그룹을 클라이언트가 구분(배지·섹션)하도록 노출한다. 목록은 {@code RECRUITING} / {@code
 * CONFIRMED} / {@code CANCELLED}(인원미달 자동취소) 를 보여주고, 개최자 취소({@code HOST_CANCELLED}) 는 제외한다.
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
