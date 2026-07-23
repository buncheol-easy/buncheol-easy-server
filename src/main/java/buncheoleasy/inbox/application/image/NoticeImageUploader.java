package buncheoleasy.inbox.application.image;

public interface NoticeImageUploader {

  /** 공지 본문 이미지를 업로드하고 접근 URL 을 반환한다. */
  String uploadNoticeImageAndGetUrl(Long noticeId, ImageFile imageFile);

  /** 홈 배너 이미지를 업로드하고 접근 URL 을 반환한다. */
  String uploadBannerImageAndGetUrl(Long noticeId, ImageFile imageFile);
}
