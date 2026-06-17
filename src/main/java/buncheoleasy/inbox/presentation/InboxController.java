package buncheoleasy.inbox.presentation;

import buncheoleasy.global.page.Cursor;
import buncheoleasy.inbox.application.InboxQueryService;
import buncheoleasy.inbox.domain.InboxMessageType;
import buncheoleasy.inbox.dto.response.InboxMessageDetailResponse;
import buncheoleasy.inbox.dto.response.InboxResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/inbox")
@RequiredArgsConstructor
public class InboxController {

  private final InboxQueryService inboxQueryService;

  /**
   * 수신함 목록 조회 (비로그인 허용). {@code type} 미지정 시 전체(공지 + 본인 알림), {@code NOTICE}/{@code NOTIFICATION}
   * 으로 필터링한다. 등록 최신순 커서 페이지네이션이며, 상단 고정 공지는 첫 페이지 응답의 {@code pinned} 로 분리해 내려간다.
   *
   * <p>비로그인 호출 시 익명 principal 은 {@code Long} 캐스팅에 실패해 {@code userId} 가 null 로 들어오며, 이때는 공지만 보인다.
   */
  @GetMapping
  public ResponseEntity<InboxResponse> getInbox(
      @AuthenticationPrincipal final Long userId,
      @RequestParam(required = false) final InboxMessageType type,
      @RequestParam(required = false) final String cursor,
      @RequestParam(defaultValue = "20") final int size) {
    return ResponseEntity.ok(inboxQueryService.getInbox(userId, type, Cursor.parse(cursor), size));
  }

  /** 수신함 단건 상세 조회 (비로그인 허용). 공지이거나 본인 알림이 아니면 404 (익명은 알림 조회 불가). */
  @GetMapping("/{messageId}")
  public ResponseEntity<InboxMessageDetailResponse> getInboxMessage(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long messageId) {
    return ResponseEntity.ok(inboxQueryService.getInboxMessage(userId, messageId));
  }
}
