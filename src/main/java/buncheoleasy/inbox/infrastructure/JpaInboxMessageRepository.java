package buncheoleasy.inbox.infrastructure;

import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaInboxMessageRepository extends JpaRepository<InboxMessage, Long> {

  /**
   * 수신함 본문 피드(고정 제외). 공지({@code type = NOTICE})와 {@code userId} 본인 알림({@code recipientId =
   * userId}) 중 {@code includeNotices}/{@code includeNotifications} 플래그로 켜진 것만 조회한다. 공지(recipientId
   * null)와 본인 알림은 서로 겹치지 않으므로 OR 조합이 안전하다. 정렬은 {@code createdAt DESC, id DESC}, 페이지 사이즈는 {@link
   * Pageable} 로 제어한다.
   */
  @Query(
      "SELECT m FROM InboxMessage m "
          + "WHERE m.pinned = FALSE "
          + "  AND ( (:includeNotices = TRUE AND m.type = :noticeType) "
          + "        OR (:includeNotifications = TRUE AND m.recipientId = :userId) ) "
          + "  AND (:cursorCreatedAt IS NULL "
          + "        OR m.createdAt < :cursorCreatedAt "
          + "        OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) "
          + "ORDER BY m.createdAt DESC, m.id DESC")
  List<InboxMessage> findFeed(
      @Param("userId") Long userId,
      @Param("includeNotices") boolean includeNotices,
      @Param("includeNotifications") boolean includeNotifications,
      @Param("noticeType") InboxMessageType noticeType,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  @Query(
      "SELECT m FROM InboxMessage m "
          + "WHERE m.type = :noticeType AND m.pinned = TRUE "
          + "ORDER BY m.createdAt DESC, m.id DESC")
  List<InboxMessage> findPinnedNotices(
      @Param("noticeType") InboxMessageType noticeType, Pageable pageable);

  @Query(
      "SELECT m FROM InboxMessage m "
          + "WHERE m.id = :id "
          + "  AND (m.type = :noticeType OR m.recipientId = :userId)")
  Optional<InboxMessage> findVisibleById(
      @Param("id") Long id,
      @Param("userId") Long userId,
      @Param("noticeType") InboxMessageType noticeType);
}
