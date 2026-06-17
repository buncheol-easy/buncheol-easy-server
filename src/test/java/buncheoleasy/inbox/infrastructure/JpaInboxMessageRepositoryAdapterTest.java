package buncheoleasy.inbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaInboxMessageRepositoryAdapter 테스트")
class JpaInboxMessageRepositoryAdapterTest {

  @Autowired private InboxMessageRepository inboxMessageRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long userId;
  private Long otherUserId;

  @BeforeEach
  void setUp() {
    userId = TestUserFixture.insertUser(jdbcTemplate, "inbox_user");
    otherUserId = TestUserFixture.insertUser(jdbcTemplate, "inbox_other");
  }

  private Long persistNotice(boolean pinned, Instant createdAt) {
    InboxMessage notice = InboxMessage.createNotice("공지 제목", "참고", "공지 설명", pinned, "/notice");
    return persistWithCreatedAt(notice, createdAt);
  }

  private Long persistNotification(Long recipientId, Instant createdAt) {
    InboxMessage notification =
        InboxMessage.createNotification(recipientId, "알림 제목", "분철명", "알림 설명", "/profile/bids");
    return persistWithCreatedAt(notification, createdAt);
  }

  private Long persistWithCreatedAt(InboxMessage message, Instant createdAt) {
    inboxMessageRepository.save(message);
    em.flush();
    jdbcTemplate.update(
        "UPDATE inbox_messages SET created_at = ? WHERE id = ?",
        Timestamp.from(createdAt),
        message.getId());
    em.clear();
    return message.getId();
  }

  @Nested
  @DisplayName("저장 테스트")
  class SaveTest {

    @Test
    void 메시지를_저장하면_ID가_할당된다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);

      inboxMessageRepository.save(notice);
      em.flush();

      assertThat(notice.getId()).isNotNull();
      assertThat(notice.getId()).isPositive();
    }
  }

  @Nested
  @DisplayName("상단 고정 더티체킹 영속화 테스트")
  class PinPersistenceTest {

    @Test
    void managed_공지를_pin하면_더티체킹으로_DB에_반영된다() {
      Long notice = persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));

      // findById 로 managed 상태로 로드 후 도메인 메서드만 호출 → flush 시 dirty UPDATE 발생
      InboxMessage managed = inboxMessageRepository.findById(notice).orElseThrow();
      managed.pin();
      em.flush();
      em.clear();

      assertThat(inboxMessageRepository.findById(notice).orElseThrow().isPinned()).isTrue();
    }

    @Test
    void managed_공지를_unpin하면_더티체킹으로_DB에_반영된다() {
      Long notice = persistNotice(true, Instant.parse("2026-06-15T08:00:00Z"));

      InboxMessage managed = inboxMessageRepository.findById(notice).orElseThrow();
      managed.unpin();
      em.flush();
      em.clear();

      assertThat(inboxMessageRepository.findById(notice).orElseThrow().isPinned()).isFalse();
    }

    @Test
    void 이미_고정된_공지를_다시_pin해도_고정_상태가_유지된다() {
      Long notice = persistNotice(true, Instant.parse("2026-06-15T08:00:00Z"));

      InboxMessage managed = inboxMessageRepository.findById(notice).orElseThrow();
      managed.pin();
      em.flush();
      em.clear();

      assertThat(inboxMessageRepository.findById(notice).orElseThrow().isPinned()).isTrue();
    }
  }

  @Nested
  @DisplayName("본문 피드(findFeed) 테스트")
  class FindFeedTest {

    @Test
    void 전체_조회는_공지와_본인_알림을_최신순으로_반환한다() {
      Long notice = persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));
      Long myNotification = persistNotification(userId, Instant.parse("2026-06-14T08:00:00Z"));

      List<InboxMessage> result =
          inboxMessageRepository.findFeed(userId, true, true, Cursor.firstPage(), 10);

      assertThat(result).extracting(InboxMessage::getId).containsExactly(notice, myNotification);
    }

    @Test
    void 공지만_필터는_알림을_제외한다() {
      Long notice = persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));
      persistNotification(userId, Instant.parse("2026-06-14T08:00:00Z"));

      List<InboxMessage> result =
          inboxMessageRepository.findFeed(userId, true, false, Cursor.firstPage(), 10);

      assertThat(result).extracting(InboxMessage::getId).containsExactly(notice);
    }

    @Test
    void 알림만_필터는_공지를_제외한다() {
      persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));
      Long myNotification = persistNotification(userId, Instant.parse("2026-06-14T08:00:00Z"));

      List<InboxMessage> result =
          inboxMessageRepository.findFeed(userId, false, true, Cursor.firstPage(), 10);

      assertThat(result).extracting(InboxMessage::getId).containsExactly(myNotification);
    }

    @Test
    void 고정된_공지는_본문_피드에서_제외된다() {
      persistNotice(true, Instant.parse("2026-06-15T08:00:00Z"));
      Long normalNotice = persistNotice(false, Instant.parse("2026-06-14T08:00:00Z"));

      List<InboxMessage> result =
          inboxMessageRepository.findFeed(userId, true, true, Cursor.firstPage(), 10);

      assertThat(result).extracting(InboxMessage::getId).containsExactly(normalNotice);
    }

    @Test
    void 다른_사용자의_알림은_반환되지_않는다() {
      Long mine = persistNotification(userId, Instant.parse("2026-06-15T08:00:00Z"));
      persistNotification(otherUserId, Instant.parse("2026-06-14T08:00:00Z"));

      List<InboxMessage> result =
          inboxMessageRepository.findFeed(userId, true, true, Cursor.firstPage(), 10);

      assertThat(result).extracting(InboxMessage::getId).containsExactly(mine);
    }

    @Test
    void 커서를_주면_그_이전_메시지만_반환된다() {
      Long m1 = persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));
      Long m2 = persistNotification(userId, Instant.parse("2026-06-14T08:00:00Z"));
      Long m3 = persistNotice(false, Instant.parse("2026-06-13T08:00:00Z"));

      List<InboxMessage> firstPage =
          inboxMessageRepository.findFeed(userId, true, true, Cursor.firstPage(), 10);
      assertThat(firstPage).extracting(InboxMessage::getId).containsExactly(m1, m2, m3);

      Cursor cursor = new Cursor(Instant.parse("2026-06-14T08:00:00Z"), m2);
      List<InboxMessage> secondPage =
          inboxMessageRepository.findFeed(userId, true, true, cursor, 10);

      assertThat(secondPage).extracting(InboxMessage::getId).containsExactly(m3);
    }

    @Test
    void 동일_createdAt_에서는_id_DESC_로_정렬된다() {
      Instant same = Instant.parse("2026-06-15T08:00:00Z");
      Long m1 = persistNotice(false, same);
      Long m2 = persistNotice(false, same);

      List<InboxMessage> result =
          inboxMessageRepository.findFeed(userId, true, true, Cursor.firstPage(), 10);

      assertThat(result).extracting(InboxMessage::getId).containsExactly(m2, m1);
    }

    @Test
    void limit보다_많이_존재하면_limit개까지만_반환한다() {
      persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));
      persistNotice(false, Instant.parse("2026-06-14T08:00:00Z"));
      persistNotice(false, Instant.parse("2026-06-13T08:00:00Z"));

      // findFeed 는 limit 을 정확히 지킨다(3건 존재해도 limit=2 면 2건만). hasNext 판별용 size+1 은
      // 서비스(InboxQueryService)가 limit=size+1 로 호출해 처리하고, 리포지토리는 limit 만 책임진다.
      List<InboxMessage> result =
          inboxMessageRepository.findFeed(userId, true, true, Cursor.firstPage(), 2);

      assertThat(result).hasSize(2);
    }

    @Test
    void 동일_createdAt_에서_커서의_id_보다_작은_id_만_반환된다() {
      Instant same = Instant.parse("2026-06-15T08:00:00Z");
      Long m1 = persistNotice(false, same);
      Long m2 = persistNotice(false, same);
      persistNotice(false, same);
      // auto-increment 라 m1 < m2 < m3. 정렬은 id DESC 이므로 m2 를 커서로 주면 그보다 작은 id(m1)만 남는다.

      Cursor cursor = new Cursor(same, m2);
      List<InboxMessage> result = inboxMessageRepository.findFeed(userId, true, true, cursor, 10);

      assertThat(result).extracting(InboxMessage::getId).containsExactly(m1);
    }
  }

  @Nested
  @DisplayName("상단 고정 공지(findPinnedNotices) 테스트")
  class FindPinnedNoticesTest {

    @Test
    void 고정된_공지만_최신순으로_반환한다() {
      Long pinnedNew = persistNotice(true, Instant.parse("2026-06-15T08:00:00Z"));
      Long pinnedOld = persistNotice(true, Instant.parse("2026-06-10T08:00:00Z"));
      persistNotice(false, Instant.parse("2026-06-14T08:00:00Z"));
      persistNotification(userId, Instant.parse("2026-06-16T08:00:00Z"));

      List<InboxMessage> result = inboxMessageRepository.findPinnedNotices();

      assertThat(result).extracting(InboxMessage::getId).containsExactly(pinnedNew, pinnedOld);
    }
  }

  @Nested
  @DisplayName("가시성 단건 조회(findVisibleById) 테스트")
  class FindVisibleByIdTest {

    @Test
    void 공지는_누구나_조회할_수_있다() {
      Long notice = persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));

      assertThat(inboxMessageRepository.findVisibleById(notice, userId)).isPresent();
      assertThat(inboxMessageRepository.findVisibleById(notice, otherUserId)).isPresent();
    }

    @Test
    void 알림은_수신자_본인만_조회할_수_있다() {
      Long notification = persistNotification(userId, Instant.parse("2026-06-15T08:00:00Z"));

      assertThat(inboxMessageRepository.findVisibleById(notification, userId)).isPresent();
      assertThat(inboxMessageRepository.findVisibleById(notification, otherUserId)).isEmpty();
    }

    @Test
    void 비회원_은_공지는_보지만_알림은_보지_못한다() {
      Long notice = persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));
      Long notification = persistNotification(userId, Instant.parse("2026-06-14T08:00:00Z"));

      // userId == null (비로그인)
      assertThat(inboxMessageRepository.findVisibleById(notice, null)).isPresent();
      assertThat(inboxMessageRepository.findVisibleById(notification, null)).isEmpty();
    }
  }

  @Nested
  @DisplayName("비회원 피드 조회 테스트")
  class AnonymousFeedTest {

    @Test
    void 비회원_은_공지만_조회하고_알림은_제외된다() {
      Long notice = persistNotice(false, Instant.parse("2026-06-15T08:00:00Z"));
      persistNotification(userId, Instant.parse("2026-06-14T08:00:00Z"));

      // 비로그인: includeNotices=true, includeNotifications=false, userId=null
      List<InboxMessage> result =
          inboxMessageRepository.findFeed(null, true, false, Cursor.firstPage(), 10);

      assertThat(result).extracting(InboxMessage::getId).containsExactly(notice);
    }
  }
}
