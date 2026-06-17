package buncheoleasy.inbox.infrastructure;

import buncheoleasy.global.page.Cursor;
import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.domain.InboxMessageType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaInboxMessageRepositoryAdapter implements InboxMessageRepository {

  // 상단 고정 공지는 소수만 운영하는 전제. 실수로 다량 등록돼도 첫 페이지 prepend 가 폭주하지 않도록 상한을 둔다.
  private static final int MAX_PINNED_NOTICES = 20;

  private final JpaInboxMessageRepository jpaInboxMessageRepository;

  @Override
  public InboxMessage save(final InboxMessage message) {
    return jpaInboxMessageRepository.save(message);
  }

  @Override
  public Optional<InboxMessage> findById(final Long id) {
    return jpaInboxMessageRepository.findById(id);
  }

  @Override
  public List<InboxMessage> findFeed(
      final Long userId,
      final boolean includeNotices,
      final boolean includeNotifications,
      final Cursor cursor,
      final int limit) {
    final Instant cursorCreatedAt = cursor.isFirstPage() ? null : cursor.createdAt();
    final Long cursorId = cursor.isFirstPage() ? null : cursor.id();
    return jpaInboxMessageRepository.findFeed(
        userId,
        includeNotices,
        includeNotifications,
        InboxMessageType.NOTICE,
        cursorCreatedAt,
        cursorId,
        PageRequest.of(0, limit));
  }

  @Override
  public List<InboxMessage> findPinnedNotices() {
    return jpaInboxMessageRepository.findPinnedNotices(
        InboxMessageType.NOTICE, PageRequest.of(0, MAX_PINNED_NOTICES));
  }

  @Override
  public Optional<InboxMessage> findVisibleById(final Long id, final Long userId) {
    return jpaInboxMessageRepository.findVisibleById(id, userId, InboxMessageType.NOTICE);
  }
}
