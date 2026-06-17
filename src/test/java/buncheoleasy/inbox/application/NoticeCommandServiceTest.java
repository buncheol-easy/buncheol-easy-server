package buncheoleasy.inbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.domain.InboxMessageType;
import buncheoleasy.inbox.dto.request.CreateNoticeRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoticeCommandService 테스트")
class NoticeCommandServiceTest {

  @Mock private InboxMessageRepository inboxMessageRepository;

  @InjectMocks private NoticeCommandService noticeCommandService;

  @Test
  void 공지를_생성하면_NOTICE_타입으로_저장한다() {
    CreateNoticeRequest request =
        new CreateNoticeRequest("점검 공지", "결제 일시 중단", "6/20 점검 예정", true, "/notice");
    given(inboxMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    noticeCommandService.createNotice(request);

    ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
    verify(inboxMessageRepository).save(captor.capture());
    InboxMessage saved = captor.getValue();
    assertThat(saved.getType()).isEqualTo(InboxMessageType.NOTICE);
    assertThat(saved.getRecipientId()).isNull();
    assertThat(saved.getTitle()).isEqualTo("점검 공지");
    assertThat(saved.isPinned()).isTrue();
    assertThat(saved.getLinkPath()).isEqualTo("/notice");
  }

  @Test
  void pinned가_null이면_고정하지_않은_공지로_저장한다() {
    CreateNoticeRequest request =
        new CreateNoticeRequest("제목", null, "설명", null, null);
    given(inboxMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    noticeCommandService.createNotice(request);

    ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
    verify(inboxMessageRepository).save(captor.capture());
    assertThat(captor.getValue().isPinned()).isFalse();
  }

  @Nested
  @DisplayName("상단 고정(pin/unpin) 테스트")
  class PinTest {

    @Test
    void 공지를_고정하면_managed_엔티티의_pinned가_true가_된다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);
      given(inboxMessageRepository.findById(1L)).willReturn(Optional.of(notice));

      noticeCommandService.pinNotice(1L);

      // 더티체킹 경로라 save 호출 없이 managed 엔티티 상태가 바뀐다.
      assertThat(notice.isPinned()).isTrue();
    }

    @Test
    void 공지_고정을_해제하면_pinned가_false가_된다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", true, null);
      given(inboxMessageRepository.findById(1L)).willReturn(Optional.of(notice));

      noticeCommandService.unpinNotice(1L);

      assertThat(notice.isPinned()).isFalse();
    }

    @Test
    void 존재하지_않는_메시지를_고정하면_NOT_FOUND_예외가_발생한다() {
      given(inboxMessageRepository.findById(404L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> noticeCommandService.pinNotice(404L))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_MESSAGE_NOT_FOUND);
    }

    @Test
    void 알림을_고정하려_하면_PIN_NOT_ALLOWED_예외가_발생한다() {
      InboxMessage notification =
          InboxMessage.createNotification(7L, "제목", null, "설명", null);
      given(inboxMessageRepository.findById(2L)).willReturn(Optional.of(notification));

      assertThatThrownBy(() -> noticeCommandService.pinNotice(2L))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_PIN_NOT_ALLOWED);
    }

    @Test
    void 알림의_고정을_해제하려_해도_PIN_NOT_ALLOWED_예외가_발생한다() {
      InboxMessage notification =
          InboxMessage.createNotification(7L, "제목", null, "설명", null);
      given(inboxMessageRepository.findById(2L)).willReturn(Optional.of(notification));

      assertThatThrownBy(() -> noticeCommandService.unpinNotice(2L))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_PIN_NOT_ALLOWED);
    }
  }
}
