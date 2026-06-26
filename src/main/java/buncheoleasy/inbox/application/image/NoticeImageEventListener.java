package buncheoleasy.inbox.application.image;

import buncheoleasy.inbox.application.NoticeCommandService;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 공지 이미지/배너의 비동기 S3 업로드 처리. 공지 트랜잭션 커밋 후 본문 이미지·배너 이미지를 올리고, 성공분을 단일 트랜잭션으로 한 번에 공지에 반영한다(같은 row
 * 동시 갱신 방지 — 단일 writer). 업로드 실패는 항목별로 로깅만 하고 삼킨다(분철 이미지와 동일한 best-effort — 이미지 실패가 공지 생성을 되돌리지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeImageEventListener {

  private final NoticeImageUploader imageUploader;
  private final NoticeCommandService noticeCommandService;

  @TransactionalEventListener
  @Async
  public void handleNoticeImageUpload(final NoticeImageUploadEvent event) {
    final Long noticeId = event.noticeId();

    final String imageUrl =
        event.image() == null
            ? null
            : tryUpload(
                noticeId, "본문 이미지", () -> imageUploader.uploadNoticeImageAndGetUrl(noticeId, event.image()));
    final String bannerImageUrl =
        event.bannerImage() == null
            ? null
            : tryUpload(
                noticeId,
                "배너 이미지",
                () -> imageUploader.uploadBannerImageAndGetUrl(noticeId, event.bannerImage()));

    if (imageUrl != null || bannerImageUrl != null) {
      noticeCommandService.attachNoticeAssets(
          noticeId, imageUrl, event.bannerTitle(), bannerImageUrl);
    }
  }

  private String tryUpload(final Long noticeId, final String label, final Supplier<String> upload) {
    try {
      return upload.get();
    } catch (RuntimeException e) {
      log.error("공지 {} 업로드 실패. noticeId={}", label, noticeId, e);
      return null;
    }
  }
}
