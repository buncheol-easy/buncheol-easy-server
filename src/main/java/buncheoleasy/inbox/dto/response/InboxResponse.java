package buncheoleasy.inbox.dto.response;

import buncheoleasy.global.page.CursorResponse;
import java.util.List;

/**
 * 수신함 목록 응답. 상단 고정 공지({@code pinned})를 본문 피드({@code feed})와 분리해 내려준다. 첫 페이지가 아니거나 알림만 조회하는 경우
 * {@code pinned} 는 빈 리스트다. {@code feed} 는 등록 최신순 커서 페이지네이션 봉투다.
 */
public record InboxResponse(
    List<InboxMessageSummaryResponse> pinned, CursorResponse<InboxMessageSummaryResponse> feed) {}
