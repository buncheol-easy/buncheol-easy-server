package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.List;

/**
 * 내 찜한 분철 목록 조회 응답의 단일 카드.
 *
 * <p>{@code memberNames} 는 분철의 전체 멤버 이름, {@code availableMemberNames} 는 <b>지금 신청할 수 있는</b> 멤버 이름이다.
 * 둘 다 호스트가 등록한 슬롯 순서로 정렬되며 계산 기준은 공개 목록({@link BuncheolSummaryResponse})과 동일하다.
 *
 * <p>{@code availableMemberNames} 는 <b>{@code status} 와 함께 해석할 필요가 없다</b> — 신규 참여를 받지 않는 분철(취소·진행확정·마감
 * 경과)은 슬롯이 비어 있어도 <b>빈 배열</b>로 내려간다. 상세 조회가 같은 슬롯을 {@code CLOSED} 로 내리는 것과 같은 판정이다
 * (docs/56 F-2). 슬롯이 없거나 전 슬롯에 활성 참여가 있을 때도 빈 배열이다.
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
