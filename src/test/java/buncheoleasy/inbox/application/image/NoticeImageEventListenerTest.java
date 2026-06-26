package buncheoleasy.inbox.application.image;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.application.NoticeCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoticeImageEventListener 테스트")
class NoticeImageEventListenerTest {

  private static final Long NOTICE_ID = 1L;

  @Mock private NoticeImageUploader imageUploader;

  @Mock private NoticeCommandService noticeCommandService;

  @InjectMocks private NoticeImageEventListener listener;

  private ImageFile imageFile(final String name) {
    return new ImageFile(name, "image/jpeg", new byte[] {1, 2, 3});
  }

  @Test
  void 본문_이미지와_배너를_모두_업로드해_단일_반영한다() {
    ImageFile image = imageFile("notice.jpg");
    ImageFile banner = imageFile("banner.jpg");
    given(imageUploader.uploadNoticeImageAndGetUrl(NOTICE_ID, image))
        .willReturn("https://cdn.example.com/n.jpg");
    given(imageUploader.uploadBannerImageAndGetUrl(NOTICE_ID, banner))
        .willReturn("https://cdn.example.com/b.jpg");

    listener.handleNoticeImageUpload(
        new NoticeImageUploadEvent(NOTICE_ID, image, "여름 이벤트", banner));

    then(noticeCommandService)
        .should()
        .attachNoticeAssets(
            NOTICE_ID,
            "https://cdn.example.com/n.jpg",
            "여름 이벤트",
            "https://cdn.example.com/b.jpg");
  }

  @Test
  void 본문_이미지만_있으면_배너는_업로드하지_않고_null로_반영한다() {
    ImageFile image = imageFile("notice.jpg");
    given(imageUploader.uploadNoticeImageAndGetUrl(NOTICE_ID, image))
        .willReturn("https://cdn.example.com/n.jpg");

    listener.handleNoticeImageUpload(new NoticeImageUploadEvent(NOTICE_ID, image, null, null));

    then(noticeCommandService)
        .should()
        .attachNoticeAssets(NOTICE_ID, "https://cdn.example.com/n.jpg", null, null);
    then(imageUploader).should(never()).uploadBannerImageAndGetUrl(any(), any());
  }

  @Test
  void 업로드가_실패하면_예외를_전파하지_않고_반영도_하지_않는다() {
    ImageFile image = imageFile("notice.jpg");
    given(imageUploader.uploadNoticeImageAndGetUrl(NOTICE_ID, image))
        .willThrow(new RuntimeException("S3 down"));

    assertThatCode(
            () ->
                listener.handleNoticeImageUpload(
                    new NoticeImageUploadEvent(NOTICE_ID, image, null, null)))
        .doesNotThrowAnyException();

    then(noticeCommandService).should(never()).attachNoticeAssets(any(), any(), any(), any());
  }

  @Test
  void 배너_업로드만_실패하면_본문만_반영한다() {
    ImageFile image = imageFile("notice.jpg");
    ImageFile banner = imageFile("banner.jpg");
    given(imageUploader.uploadNoticeImageAndGetUrl(NOTICE_ID, image))
        .willReturn("https://cdn.example.com/n.jpg");
    given(imageUploader.uploadBannerImageAndGetUrl(NOTICE_ID, banner))
        .willThrow(new RuntimeException("S3 down"));

    listener.handleNoticeImageUpload(
        new NoticeImageUploadEvent(NOTICE_ID, image, "여름 이벤트", banner));

    // 배너는 업로드 실패로 null → 본문만 반영(부분 성공). 배너 제목이 있어도 URL 이 없으면 서비스가 배너를 건너뛴다.
    then(noticeCommandService)
        .should()
        .attachNoticeAssets(NOTICE_ID, "https://cdn.example.com/n.jpg", "여름 이벤트", null);
  }

  @Test
  void DB_반영이_실패해도_예외를_전파하지_않는다() {
    ImageFile image = imageFile("notice.jpg");
    given(imageUploader.uploadNoticeImageAndGetUrl(NOTICE_ID, image))
        .willReturn("https://cdn.example.com/n.jpg");
    willThrow(new BusinessException(ErrorCode.INBOX_MESSAGE_NOT_FOUND))
        .given(noticeCommandService)
        .attachNoticeAssets(any(), any(), any(), any());

    assertThatCode(
            () ->
                listener.handleNoticeImageUpload(
                    new NoticeImageUploadEvent(NOTICE_ID, image, null, null)))
        .doesNotThrowAnyException();
  }
}
