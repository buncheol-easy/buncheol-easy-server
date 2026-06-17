package buncheoleasy.inbox.dto.response;

import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageType;
import java.time.Instant;

/**
 * 수신함 상세. 제목·참고·설명·종류·등록일자·고정여부와 함께, 연관 화면이 있으면 이동 경로({@code linkPath})를 노출한다(없으면 null).
 */
public record InboxMessageDetailResponse(
    Long id,
    String title,
    String reference,
    String description,
    InboxMessageType type,
    Instant createdAt,
    boolean pinned,
    String linkPath) {

  public static InboxMessageDetailResponse from(final InboxMessage message) {
    return new InboxMessageDetailResponse(
        message.getId(),
        message.getTitle(),
        message.getReference(),
        message.getDescription(),
        message.getType(),
        message.getCreatedAt(),
        message.isPinned(),
        message.getLinkPath());
  }
}
