package buncheoleasy.inbox.application;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.application.image.ImageFile;
import buncheoleasy.inbox.application.image.NoticeImageUploadEvent;
import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.dto.request.BannerCreateRequest;
import buncheoleasy.inbox.dto.request.CreateNoticeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공지 작성/상단 고정 관리. (인가는 현재 인증만 — 소유권/관리자 role 검증은 추후 고도화) */
@Service
@RequiredArgsConstructor
public class NoticeCommandService {

  private final InboxMessageRepository inboxMessageRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public Long createNotice(
      final CreateNoticeRequest request, final ImageFile image, final ImageFile bannerImage) {
    validateBanner(request.banner(), bannerImage);

    final InboxMessage notice =
        InboxMessage.createNotice(
            request.title(),
            request.reference(),
            request.description(),
            request.pinned(),
            request.linkPath());
    final Long noticeId = inboxMessageRepository.save(notice).getId();

    // 본문 이미지·배너는 커밋 후(@TransactionalEventListener) 비동기로 S3 에 올린 뒤 공지에 반영한다. 한 공지의
    // 이미지/배너를 단일 이벤트에 실어, 비동기 반영도 단일 트랜잭션(단일 writer)으로 처리해 row 동시 갱신 경합을 막는다.
    if (image != null || bannerImage != null) {
      final String bannerTitle = bannerImage != null ? request.banner().title() : null;
      eventPublisher.publishEvent(
          new NoticeImageUploadEvent(noticeId, image, bannerTitle, bannerImage));
    }
    return noticeId;
  }

  /**
   * 본문 이미지·배너 반영. 비동기 업로드 완료 후 리스너가 한 번 호출하며, 업로드에 성공한 항목만 한 트랜잭션에서 함께 반영한다(동일 공지 row 의 동시 갱신 방지).
   */
  @Transactional
  public void attachNoticeAssets(
      final Long noticeId,
      final String imageUrl,
      final String bannerTitle,
      final String bannerImageUrl) {
    final InboxMessage notice = getMessage(noticeId);
    if (imageUrl != null) {
      notice.attachImage(imageUrl);
    }
    if (bannerImageUrl != null) {
      notice.attachBanner(bannerTitle, bannerImageUrl);
    }
  }

  /** 공지 상단 고정. managed 엔티티를 더티체킹으로 갱신한다(단일 플래그 토글, 동시성 보호 불필요). */
  @Transactional
  public void pinNotice(final Long noticeId) {
    getMessage(noticeId).pin();
  }

  /** 공지 상단 고정 해제. */
  @Transactional
  public void unpinNotice(final Long noticeId) {
    getMessage(noticeId).unpin();
  }

  // 배너는 제목(request.banner)과 이미지(bannerImage)를 함께 입력해야 한다. 한쪽만 오면 거부한다.
  private void validateBanner(final BannerCreateRequest banner, final ImageFile bannerImage) {
    if ((banner != null) != (bannerImage != null)) {
      throw new BusinessException(ErrorCode.NOTICE_BANNER_INCOMPLETE);
    }
  }

  private InboxMessage getMessage(final Long noticeId) {
    return inboxMessageRepository
        .findById(noticeId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INBOX_MESSAGE_NOT_FOUND));
  }
}
