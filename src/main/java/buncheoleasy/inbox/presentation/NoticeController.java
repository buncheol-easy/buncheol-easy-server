package buncheoleasy.inbox.presentation;

import buncheoleasy.inbox.application.NoticeCommandService;
import buncheoleasy.inbox.dto.request.CreateNoticeRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

  private final NoticeCommandService noticeCommandService;

  /**
   * 공지 작성. 인증된 사용자면 작성 가능하다(소유권/관리자 role 은 추후 고도화). 인증 자체는 SecurityConfig 의 {@code
   * anyRequest().authenticated()} 가 강제한다.
   */
  @PostMapping
  public ResponseEntity<Void> createNotice(
      @Valid @RequestBody final CreateNoticeRequest request) {
    final Long noticeId = noticeCommandService.createNotice(request);
    // 생성된 공지는 수신함 상세(GET /v1/inbox/{id})로 조회한다.
    return ResponseEntity.created(URI.create("/v1/inbox/" + noticeId)).build();
  }

  /** 공지 상단 고정 등록. (소유권 검증은 추후 고도화) */
  @PutMapping("/{noticeId}/pin")
  public ResponseEntity<Void> pinNotice(@PathVariable final Long noticeId) {
    noticeCommandService.pinNotice(noticeId);
    return ResponseEntity.noContent().build();
  }

  /** 공지 상단 고정 해제. (소유권 검증은 추후 고도화) */
  @DeleteMapping("/{noticeId}/pin")
  public ResponseEntity<Void> unpinNotice(@PathVariable final Long noticeId) {
    noticeCommandService.unpinNotice(noticeId);
    return ResponseEntity.noContent().build();
  }
}
