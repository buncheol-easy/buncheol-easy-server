package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminBuncheolCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 분철 관리 API. {@code /v1/admin/**} 은 SecurityConfig 가 ROLE_ADMIN 을 강제한다. */
@RestController
@RequestMapping("/v1/admin/buncheols")
@RequiredArgsConstructor
public class AdminBuncheolController {

  private final AdminBuncheolCommandService adminBuncheolCommandService;

  /**
   * 회원 개최 분철 파기(입금 수집중, 또는 운송장 전인 진행 확정). 분철을 숨기고 입금 확인된 참여까지 전원 취소하며 참여자에게 분철 취소
   * 안내를 보낸다. 상태와 무관하게 운송장이 하나라도 있으면 409(BCH-130).
   */
  @PostMapping("/{id}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable final Long id) {
    adminBuncheolCommandService.cancel(id);
    return ResponseEntity.noContent().build();
  }
}
