package buncheoleasy.inbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.domain.InboxMessageType;
import buncheoleasy.inbox.dto.response.InboxResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InboxQueryService 테스트")
class InboxQueryServiceTest {

  private static final Long USER_ID = 1L;

  @Mock private InboxMessageRepository inboxMessageRepository;

  @InjectMocks private InboxQueryService inboxQueryService;

  private InboxMessage message(
      final long id, final Instant createdAt, final InboxMessageType type) {
    final InboxMessage m = mock(InboxMessage.class);
    given(m.getId()).willReturn(id);
    given(m.getCreatedAt()).willReturn(createdAt);
    given(m.getTitle()).willReturn("제목" + id);
    given(m.isPinned()).willReturn(false);
    given(m.getType()).willReturn(type);
    return m;
  }

  @Nested
  @DisplayName("목록 조회(getInbox) 테스트")
  class GetInboxTest {

    @Test
    void 전체_필터는_공지와_알림을_모두_포함하도록_조회한다() {
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), anyInt()))
          .willReturn(List.of());
      given(inboxMessageRepository.findPinnedNotices()).willReturn(List.of());

      inboxQueryService.getInbox(USER_ID, null, Cursor.firstPage(), 20);

      verify(inboxMessageRepository)
          .findFeed(eq(USER_ID), eq(true), eq(true), any(), eq(21));
    }

    @Test
    void 공지만_필터는_알림을_제외하도록_조회한다() {
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), anyInt()))
          .willReturn(List.of());
      given(inboxMessageRepository.findPinnedNotices()).willReturn(List.of());

      inboxQueryService.getInbox(USER_ID, InboxMessageType.NOTICE, Cursor.firstPage(), 20);

      verify(inboxMessageRepository).findFeed(eq(USER_ID), eq(true), eq(false), any(), anyInt());
    }

    @Test
    void 알림만_필터는_공지를_제외하고_고정도_조회하지_않는다() {
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), anyInt()))
          .willReturn(List.of());

      InboxResponse response =
          inboxQueryService.getInbox(USER_ID, InboxMessageType.NOTIFICATION, Cursor.firstPage(), 20);

      verify(inboxMessageRepository).findFeed(eq(USER_ID), eq(false), eq(true), any(), anyInt());
      verify(inboxMessageRepository, never()).findPinnedNotices();
      assertThat(response.pinned()).isEmpty();
    }

    @Test
    void 첫_페이지에서만_고정_공지를_prepend_한다() {
      InboxMessage pinned =
          message(99L, Instant.parse("2026-06-15T08:00:00Z"), InboxMessageType.NOTICE);
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), anyInt()))
          .willReturn(List.of());
      given(inboxMessageRepository.findPinnedNotices()).willReturn(List.of(pinned));

      InboxResponse response = inboxQueryService.getInbox(USER_ID, null, Cursor.firstPage(), 20);

      assertThat(response.pinned()).extracting("id").containsExactly(99L);
    }

    @Test
    void 비회원_전체조회는_알림을_제외하고_공지만_조회한다() {
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), anyInt()))
          .willReturn(List.of());
      given(inboxMessageRepository.findPinnedNotices()).willReturn(List.of());

      // userId == null (비로그인) → includeNotifications=false, 고정 공지는 그대로 노출
      inboxQueryService.getInbox(null, null, Cursor.firstPage(), 20);

      verify(inboxMessageRepository).findFeed(eq(null), eq(true), eq(false), any(), anyInt());
      verify(inboxMessageRepository).findPinnedNotices();
    }

    @Test
    void 두번째_페이지부터는_고정_공지를_조회하지_않는다() {
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), anyInt()))
          .willReturn(List.of());

      Cursor cursor = new Cursor(Instant.parse("2026-06-14T08:00:00Z"), 50L);
      InboxResponse response = inboxQueryService.getInbox(USER_ID, null, cursor, 20);

      verify(inboxMessageRepository, never()).findPinnedNotices();
      assertThat(response.pinned()).isEmpty();
    }

    @Test
    void size_보다_한건_더_조회되면_hasNext_와_nextCursor_가_채워진다() {
      // size=2 → limit=3 fetch. 3건 반환 시 2건만 노출하고 hasNext=true.
      List<InboxMessage> three =
          IntStream.rangeClosed(1, 3)
              .mapToObj(
                  i ->
                      message(
                          i,
                          Instant.parse("2026-06-1" + (5 - i) + "T08:00:00Z"),
                          InboxMessageType.NOTICE))
              .toList();
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), eq(3)))
          .willReturn(three);
      given(inboxMessageRepository.findPinnedNotices()).willReturn(List.of());

      InboxResponse response = inboxQueryService.getInbox(USER_ID, null, Cursor.firstPage(), 2);

      assertThat(response.feed().items()).hasSize(2);
      assertThat(response.feed().hasNext()).isTrue();
      assertThat(response.feed().nextCursor()).isNotNull();
    }

    @Test
    void 조회_결과가_size_이하면_hasNext_는_false_이고_nextCursor_는_null_이다() {
      InboxMessage single =
          message(1L, Instant.parse("2026-06-15T08:00:00Z"), InboxMessageType.NOTICE);
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), anyInt()))
          .willReturn(List.of(single));
      given(inboxMessageRepository.findPinnedNotices()).willReturn(List.of());

      InboxResponse response = inboxQueryService.getInbox(USER_ID, null, Cursor.firstPage(), 20);

      assertThat(response.feed().hasNext()).isFalse();
      assertThat(response.feed().nextCursor()).isNull();
    }

    @Test
    void size_가_범위를_벗어나면_clamp_된다() {
      given(inboxMessageRepository.findFeed(any(), anyBoolean(), anyBoolean(), any(), anyInt()))
          .willReturn(List.of());
      given(inboxMessageRepository.findPinnedNotices()).willReturn(List.of());

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);

      inboxQueryService.getInbox(USER_ID, null, Cursor.firstPage(), 1000);
      inboxQueryService.getInbox(USER_ID, null, Cursor.firstPage(), 0);

      verify(inboxMessageRepository, atLeastOnce())
          .findFeed(any(), anyBoolean(), anyBoolean(), any(), limitCaptor.capture());
      // 최대 50 + 1, 최소 1 + 1 로 clamp
      assertThat(limitCaptor.getAllValues()).containsExactly(51, 2);
    }
  }

  @Nested
  @DisplayName("상세 조회(getInboxMessage) 테스트")
  class GetInboxMessageTest {

    @Test
    void 볼_수_없는_메시지면_NOT_FOUND_예외가_발생한다() {
      given(inboxMessageRepository.findVisibleById(404L, USER_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> inboxQueryService.getInboxMessage(USER_ID, 404L))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_MESSAGE_NOT_FOUND);
    }
  }
}
