package buncheoleasy.inbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.application.image.ImageFile;
import buncheoleasy.inbox.application.image.NoticeImageUploadEvent;
import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.domain.InboxMessageType;
import buncheoleasy.inbox.dto.request.BannerCreateRequest;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoticeCommandService 테스트")
class NoticeCommandServiceTest {

  private static final Long NOTICE_ID = 42L;

  @Mock private InboxMessageRepository inboxMessageRepository;

  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private NoticeCommandService noticeCommandService;

  private CreateNoticeRequest request(final BannerCreateRequest banner) {
    return new CreateNoticeRequest("점검 공지", "결제 일시 중단", "6/20 점검 예정", true, "/notice", banner);
  }

  private ImageFile imageFile(final String name) {
    return new ImageFile(name, "image/jpeg", new byte[] {1, 2, 3});
  }

  // 저장 시 생성 id(42)를 부여해, 발행 이벤트의 noticeId 까지 검증할 수 있게 한다.
  private void givenSaveAssignsId() {
    given(inboxMessageRepository.save(any()))
        .willAnswer(
            inv -> {
              InboxMessage saved = inv.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", NOTICE_ID);
              return saved;
            });
  }

  private NoticeImageUploadEvent capturedEvent() {
    ArgumentCaptor<NoticeImageUploadEvent> captor =
        ArgumentCaptor.forClass(NoticeImageUploadEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    return captor.getValue();
  }

  @Nested
  @DisplayName("공지 생성(createNotice) 테스트")
  class CreateNoticeTest {

    @Test
    void 공지를_생성하면_NOTICE_타입으로_저장한다() {
      given(inboxMessageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      noticeCommandService.createNotice(request(null), null, null);

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
    void 이미지도_배너도_없으면_업로드_이벤트를_발행하지_않는다() {
      given(inboxMessageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      noticeCommandService.createNotice(request(null), null, null);

      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 본문_이미지가_있으면_이미지를_실은_이벤트를_발행한다() {
      givenSaveAssignsId();

      noticeCommandService.createNotice(request(null), imageFile("notice.jpg"), null);

      NoticeImageUploadEvent event = capturedEvent();
      assertThat(event.noticeId()).isEqualTo(NOTICE_ID);
      assertThat(event.image().originalFilename()).isEqualTo("notice.jpg");
      assertThat(event.bannerTitle()).isNull();
      assertThat(event.bannerImage()).isNull();
    }

    @Test
    void 배너_제목과_이미지가_함께_오면_배너를_실은_이벤트를_발행한다() {
      givenSaveAssignsId();

      noticeCommandService.createNotice(
          request(new BannerCreateRequest("여름 이벤트")), null, imageFile("banner.jpg"));

      NoticeImageUploadEvent event = capturedEvent();
      assertThat(event.noticeId()).isEqualTo(NOTICE_ID);
      assertThat(event.image()).isNull();
      assertThat(event.bannerTitle()).isEqualTo("여름 이벤트");
      assertThat(event.bannerImage().originalFilename()).isEqualTo("banner.jpg");
    }

    @Test
    void 이미지와_배너가_모두_있으면_단일_이벤트에_둘_다_실린다() {
      givenSaveAssignsId();

      noticeCommandService.createNotice(
          request(new BannerCreateRequest("여름 이벤트")),
          imageFile("notice.jpg"),
          imageFile("banner.jpg"));

      // 같은 공지 row 의 동시 갱신 경합을 피하려 이벤트는 정확히 1건이어야 한다.
      NoticeImageUploadEvent event = capturedEvent();
      assertThat(event.image().originalFilename()).isEqualTo("notice.jpg");
      assertThat(event.bannerTitle()).isEqualTo("여름 이벤트");
      assertThat(event.bannerImage().originalFilename()).isEqualTo("banner.jpg");
    }

    @Test
    void 배너_제목만_있고_이미지가_없으면_INCOMPLETE_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  noticeCommandService.createNotice(
                      request(new BannerCreateRequest("여름 이벤트")), null, null))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTICE_BANNER_INCOMPLETE);
      verify(inboxMessageRepository, never()).save(any());
    }

    @Test
    void 배너_이미지만_있고_제목이_없으면_INCOMPLETE_예외가_발생한다() {
      assertThatThrownBy(
              () -> noticeCommandService.createNotice(request(null), null, imageFile("banner.jpg")))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTICE_BANNER_INCOMPLETE);
      verify(inboxMessageRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("비동기 반영(attachNoticeAssets) 테스트")
  class AttachTest {

    @Test
    void 본문_이미지와_배너를_함께_반영하면_둘_다_채워진다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);
      given(inboxMessageRepository.findById(1L)).willReturn(Optional.of(notice));

      noticeCommandService.attachNoticeAssets(
          1L, "https://cdn.example.com/n.jpg", "여름 이벤트", "https://cdn.example.com/b.jpg");

      assertThat(notice.getImageUrl()).isEqualTo("https://cdn.example.com/n.jpg");
      assertThat(notice.getBannerTitle()).isEqualTo("여름 이벤트");
      assertThat(notice.getBannerImageUrl()).isEqualTo("https://cdn.example.com/b.jpg");
    }

    @Test
    void 본문_이미지만_반영하면_배너는_비어있다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);
      given(inboxMessageRepository.findById(1L)).willReturn(Optional.of(notice));

      noticeCommandService.attachNoticeAssets(1L, "https://cdn.example.com/n.jpg", null, null);

      assertThat(notice.getImageUrl()).isEqualTo("https://cdn.example.com/n.jpg");
      assertThat(notice.getBannerImageUrl()).isNull();
    }

    @Test
    void 배너만_반영하면_본문_이미지는_비어있다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);
      given(inboxMessageRepository.findById(1L)).willReturn(Optional.of(notice));

      noticeCommandService.attachNoticeAssets(1L, null, "여름 이벤트", "https://cdn.example.com/b.jpg");

      assertThat(notice.getImageUrl()).isNull();
      assertThat(notice.getBannerImageUrl()).isEqualTo("https://cdn.example.com/b.jpg");
    }

    @Test
    void 존재하지_않는_공지에_반영하면_NOT_FOUND_예외가_발생한다() {
      given(inboxMessageRepository.findById(404L)).willReturn(Optional.empty());

      assertThatThrownBy(
              () -> noticeCommandService.attachNoticeAssets(404L, "url", null, null))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_MESSAGE_NOT_FOUND);
    }
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
      InboxMessage notification = InboxMessage.createNotification(7L, "제목", null, "설명", null);
      given(inboxMessageRepository.findById(2L)).willReturn(Optional.of(notification));

      assertThatThrownBy(() -> noticeCommandService.pinNotice(2L))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_PIN_NOT_ALLOWED);
    }

    @Test
    void 알림의_고정을_해제하려_해도_PIN_NOT_ALLOWED_예외가_발생한다() {
      InboxMessage notification = InboxMessage.createNotification(7L, "제목", null, "설명", null);
      given(inboxMessageRepository.findById(2L)).willReturn(Optional.of(notification));

      assertThatThrownBy(() -> noticeCommandService.unpinNotice(2L))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_PIN_NOT_ALLOWED);
    }
  }
}
