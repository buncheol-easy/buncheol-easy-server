package buncheoleasy.inbox.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("InboxMessage 도메인 테스트")
class InboxMessageTest {

  @Nested
  @DisplayName("공지 생성 테스트")
  class CreateNoticeTest {

    @Test
    void 공지를_생성하면_타입은_NOTICE이고_수신자는_없다() {
      InboxMessage notice =
          InboxMessage.createNotice("점검 공지", "결제 일시 중단", "6/20 서버 점검 예정입니다.", true, "/notice");

      assertThat(notice.getType()).isEqualTo(InboxMessageType.NOTICE);
      assertThat(notice.getRecipientId()).isNull();
      assertThat(notice.isPinned()).isTrue();
      assertThat(notice.getReference()).isEqualTo("결제 일시 중단");
      assertThat(notice.getLinkPath()).isEqualTo("/notice");
    }

    @Test
    void 참고와_경로는_없어도_생성된다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);

      assertThat(notice.getReference()).isNull();
      assertThat(notice.getLinkPath()).isNull();
    }

    @Test
    void 제목이_비어있으면_예외가_발생한다() {
      assertThatThrownBy(() -> InboxMessage.createNotice(" ", null, "설명", false, null))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", ErrorCode.INBOX_MESSAGE_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 설명이_비어있으면_예외가_발생한다() {
      assertThatThrownBy(() -> InboxMessage.createNotice("제목", null, " ", false, null))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", ErrorCode.INBOX_MESSAGE_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 상대_경로_linkPath는_허용된다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, "/products/7/manage");

      assertThat(notice.getLinkPath()).isEqualTo("/products/7/manage");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "javascript:alert(1)",
          "http://evil.example.com",
          "https://evil.example.com",
          "//evil.example.com",
          "/\\evil.example.com",
          "/path\ninjected",
          "products/7/manage"
        })
    void 상대_경로가_아닌_linkPath는_예외가_발생한다(final String linkPath) {
      assertThatThrownBy(() -> InboxMessage.createNotice("제목", null, "설명", false, linkPath))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_LINK_PATH_INVALID);
    }

    @Test
    void 제목이_200자를_초과하면_예외가_발생한다() {
      String tooLong = "가".repeat(201);

      assertThatThrownBy(() -> InboxMessage.createNotice(tooLong, null, "설명", false, null))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_MESSAGE_TEXT_LENGTH_INVALID);
    }

    @Test
    void 설명이_5000자를_초과하면_예외가_발생한다() {
      String tooLong = "가".repeat(5001);

      assertThatThrownBy(() -> InboxMessage.createNotice("제목", null, tooLong, false, null))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_MESSAGE_TEXT_LENGTH_INVALID);
    }
  }

  @Nested
  @DisplayName("알림 생성 테스트")
  class CreateNotificationTest {

    @Test
    void 알림을_생성하면_타입은_NOTIFICATION이고_고정은_항상_false다() {
      InboxMessage notification =
          InboxMessage.createNotification(7L, "분철 낙찰 안내", "아이브 앨범", "낙찰되었어요.", "/profile/bids");

      assertThat(notification.getType()).isEqualTo(InboxMessageType.NOTIFICATION);
      assertThat(notification.getRecipientId()).isEqualTo(7L);
      assertThat(notification.isPinned()).isFalse();
    }

    @Test
    void 수신자가_없으면_예외가_발생한다() {
      assertThatThrownBy(
              () -> InboxMessage.createNotification(null, "제목", null, "설명", null))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", ErrorCode.INBOX_MESSAGE_REQUIRED_FIELD_MISSING);
    }
  }

  @Nested
  @DisplayName("상단 고정(pin/unpin) 테스트")
  class PinTest {

    @Test
    void 공지를_고정하면_pinned가_true가_된다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);

      notice.pin();

      assertThat(notice.isPinned()).isTrue();
    }

    @Test
    void 공지_고정을_해제하면_pinned가_false가_된다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", true, null);

      notice.unpin();

      assertThat(notice.isPinned()).isFalse();
    }

    @Test
    void 이미_고정된_공지를_다시_고정해도_멱등하게_true다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", true, null);

      notice.pin();

      assertThat(notice.isPinned()).isTrue();
    }

    @Test
    void 알림은_고정할_수_없다() {
      InboxMessage notification =
          InboxMessage.createNotification(7L, "제목", null, "설명", null);

      assertThatThrownBy(notification::pin)
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_PIN_NOT_ALLOWED);
    }

    @Test
    void 알림은_고정_해제도_할_수_없다() {
      InboxMessage notification =
          InboxMessage.createNotification(7L, "제목", null, "설명", null);

      assertThatThrownBy(notification::unpin)
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_PIN_NOT_ALLOWED);
    }
  }

  @Nested
  @DisplayName("가시성(isVisibleTo) 테스트")
  class VisibilityTest {

    @Test
    void 공지는_모든_사용자에게_보인다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);

      assertThat(notice.isVisibleTo(1L)).isTrue();
      assertThat(notice.isVisibleTo(2L)).isTrue();
      assertThat(notice.isVisibleTo(null)).isTrue();
    }

    @Test
    void 알림은_수신자_본인에게만_보인다() {
      InboxMessage notification =
          InboxMessage.createNotification(7L, "제목", null, "설명", null);

      assertThat(notification.isVisibleTo(7L)).isTrue();
      assertThat(notification.isVisibleTo(8L)).isFalse();
      assertThat(notification.isVisibleTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("이미지/배너 첨부(attach) 테스트")
  class AttachTest {

    @Test
    void 본문_이미지를_첨부하면_imageUrl이_채워진다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);

      notice.attachImage("https://cdn.example.com/n.jpg");

      assertThat(notice.getImageUrl()).isEqualTo("https://cdn.example.com/n.jpg");
    }

    @Test
    void 배너를_첨부하면_제목과_이미지가_함께_채워진다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);

      notice.attachBanner("여름 이벤트", "https://cdn.example.com/b.jpg");

      assertThat(notice.getBannerTitle()).isEqualTo("여름 이벤트");
      assertThat(notice.getBannerImageUrl()).isEqualTo("https://cdn.example.com/b.jpg");
    }

    @Test
    void 배너_제목이_비어있으면_예외가_발생한다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);

      assertThatThrownBy(() -> notice.attachBanner(" ", "https://cdn.example.com/b.jpg"))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_MESSAGE_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 배너_제목이_200자를_초과하면_예외가_발생한다() {
      InboxMessage notice = InboxMessage.createNotice("제목", null, "설명", false, null);
      String tooLong = "가".repeat(201);

      assertThatThrownBy(() -> notice.attachBanner(tooLong, "https://cdn.example.com/b.jpg"))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INBOX_MESSAGE_TEXT_LENGTH_INVALID);
    }
  }
}
