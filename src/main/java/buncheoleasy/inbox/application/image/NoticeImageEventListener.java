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
      try {
        noticeCommandService.attachNoticeAssets(
            noticeId, imageUrl, event.bannerTitle(), bannerImageUrl);
      } catch (RuntimeException e) {
        // DB 반영 실패(공지 삭제·일시 오류 등) 시 업로드된 S3 객체는 고아가 되지만, 리스너는 best-effort 라
        // 예외를 전파하지 않고 식별 정보와 함께 로깅만 한다(업로드 단계와 동일 정책).
        log.error(
            "공지 자산 반영 실패. noticeId={}, imageUrl={}, bannerImageUrl={}",
            noticeId,
            imageUrl,
            bannerImageUrl,
            e);
      }
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
