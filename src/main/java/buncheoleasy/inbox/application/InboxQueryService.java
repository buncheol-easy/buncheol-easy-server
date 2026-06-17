package buncheoleasy.inbox.application;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.domain.InboxMessageType;
import buncheoleasy.inbox.dto.response.InboxMessageDetailResponse;
import buncheoleasy.inbox.dto.response.InboxMessageSummaryResponse;
import buncheoleasy.inbox.dto.response.InboxResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수신함 조회. 공지(전체 대상)와 본인 알림을 등록 최신순으로 합쳐 보여주며, {@code filter} 로 전체/공지/알림을 가른다.
 *
 * <p>상단 고정 공지는 본문 피드와 분리해, 첫 페이지(커서 없음)에서만 별도 조회로 prepend 한다. 무한스크롤 피드는 {@code pinned = false} 만
 * 대상으로 size+1 fetch 후 hasNext 를 판별한다(= {@code BuncheolListQueryService} 패턴).
 *
 * <p>비로그인 조회도 허용한다(공개 GET 경로). 이때 {@code userId} 가 null 이며 공지만 보인다 — 알림은 본인 것만 노출되도록 두 메서드 모두
 * 방어한다(목록은 {@code includeNotifications=false}, 상세는 {@code recipientId = userId} 비교로 타인·익명에게 알림 비노출).
 */
@Service
@RequiredArgsConstructor
public class InboxQueryService {

  private static final int MIN_SIZE = 1;
  private static final int MAX_SIZE = 50;

  private final InboxMessageRepository inboxMessageRepository;

  @Transactional(readOnly = true)
  public InboxResponse getInbox(
      final Long userId,
      final InboxMessageType filter,
      final Cursor cursor,
      final int requestedSize) {
    final boolean includeNotices = filter != InboxMessageType.NOTIFICATION;
    // 비로그인(userId=null)은 알림을 못 본다. SQL 의 recipientId=null 비교(UNKNOWN 제외)에만 의존하지 않고,
    // 여기서 알림 가지를 명시적으로 꺼 비회원에게 타인 알림이 새지 않도록 한다(비회원 알림 비노출의 1차 방어선).
    final boolean includeNotifications = filter != InboxMessageType.NOTICE && userId != null;

    final int safeSize = clampSize(requestedSize);
    final List<InboxMessage> fetched =
        inboxMessageRepository.findFeed(
            userId, includeNotices, includeNotifications, cursor, safeSize + 1);
    final boolean hasNext = fetched.size() > safeSize;
    final List<InboxMessage> visible = hasNext ? fetched.subList(0, safeSize) : fetched;

    final List<InboxMessageSummaryResponse> feedItems =
        visible.stream().map(InboxMessageSummaryResponse::from).toList();
    final String nextCursor = hasNext ? Cursor.from(visible.getLast()).encode() : null;
    final CursorResponse<InboxMessageSummaryResponse> feed =
        new CursorResponse<>(feedItems, nextCursor, hasNext);

    return new InboxResponse(loadPinned(cursor, includeNotices), feed);
  }

  @Transactional(readOnly = true)
  public InboxMessageDetailResponse getInboxMessage(final Long userId, final Long messageId) {
    final InboxMessage message =
        inboxMessageRepository
            .findVisibleById(messageId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INBOX_MESSAGE_NOT_FOUND));
    return InboxMessageDetailResponse.from(message);
  }

  // 고정 공지는 첫 페이지에서만, 공지를 포함하는 필터(전체/공지)에서만 prepend 한다.
  private List<InboxMessageSummaryResponse> loadPinned(
      final Cursor cursor, final boolean includeNotices) {
    if (!cursor.isFirstPage() || !includeNotices) {
      return List.of();
    }
    return inboxMessageRepository.findPinnedNotices().stream()
        .map(InboxMessageSummaryResponse::from)
        .toList();
  }

  private int clampSize(final int requested) {
    return Math.max(MIN_SIZE, Math.min(requested, MAX_SIZE));
  }
}
