package buncheoleasy.inbox.application;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.dto.request.CreateNoticeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공지 작성/상단 고정 관리. (인가는 현재 인증만 — 소유권/관리자 role 검증은 추후 고도화) */
@Service
@RequiredArgsConstructor
public class NoticeCommandService {

  private final InboxMessageRepository inboxMessageRepository;

  @Transactional
  public Long createNotice(final CreateNoticeRequest request) {
    final InboxMessage notice =
        InboxMessage.createNotice(
            request.title(),
            request.reference(),
            request.description(),
            request.pinned(),
            request.linkPath());
    return inboxMessageRepository.save(notice).getId();
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

  private InboxMessage getMessage(final Long noticeId) {
    return inboxMessageRepository
        .findById(noticeId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INBOX_MESSAGE_NOT_FOUND));
  }
}
