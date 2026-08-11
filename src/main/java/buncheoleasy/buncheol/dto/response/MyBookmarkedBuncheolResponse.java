package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.List;

/**
 * 내 찜한 분철 목록 조회 응답의 단일 카드.
 *
 * <p>{@code memberNames} 는 분철의 전체 멤버 이름, {@code availableMemberNames} 는 활성 참여가 없는 슬롯의 멤버 이름이다.
 * 둘 다 호스트가 등록한 슬롯 순서로 정렬되며 계산 기준은 공개 목록({@link BuncheolSummaryResponse})과 동일하다.
 *
 * <p>{@code availableMemberNames} 는 {@code status} 와 함께 해석해야 한다 — 취소({@code CANCELLED})·마감({@code CONFIRMED})
 * 분철은 활성 참여가 해제·부재해 슬롯이 남아 보여도 실제로 구매할 수 없다. 판매 가능 여부 표시는 모집중({@code RECRUITING})·입금
 * 수집중({@code PAYMENT_COLLECTING}) 상태에서만 유효하다. 슬롯이 없거나 전 슬롯에 활성 참여가 있으면 빈 배열이다.
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
