package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.List;

/**
 * 내 찜한 분철 목록 조회 응답의 단일 카드.
 *
 * <p>{@code memberNames} 는 분철의 전체 멤버 이름, {@code availableMemberNames} 는 아직 안 팔린(활성 참여가 없는) 멤버 이름이다.
 * 둘 다 호스트가 등록한 슬롯 순서로 정렬되며 기준은 공개 목록({@link BuncheolSummaryResponse})과 동일하다. 전 슬롯이 팔린 분철의
 * {@code availableMemberNames} 는 빈 배열이다.
 */
public record MyBookmarkedBuncheolResponse(
    Long bookmarkId,
    Long buncheolId,
    String title,
    BuncheolStatus status,
    Instant deadline,
    String groupName,
    String thumbnailUrl,
    List<String> memberNames,
    List<String> availableMemberNames) {}
