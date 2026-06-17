package buncheoleasy.inbox.dto.response;

import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageType;
import java.time.Instant;

/** 수신함 목록 항목. 제목·상단고정여부·종류·등록일자를 노출한다. */
public record InboxMessageSummaryResponse(
    Long id, String title, boolean pinned, InboxMessageType type, Instant createdAt) {

  public static InboxMessageSummaryResponse from(final InboxMessage message) {
    return new InboxMessageSummaryResponse(
        message.getId(),
        message.getTitle(),
        message.isPinned(),
        message.getType(),
        message.getCreatedAt());
  }
}
