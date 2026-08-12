package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminImpersonationService;
import buncheoleasy.admin.dto.request.AdminImpersonationTokenRequest;
import buncheoleasy.admin.dto.response.AdminImpersonationTokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 impersonation API. {@code /v1/admin/**} 은 SecurityConfig 가 ROLE_ADMIN 을 강제한다 — 관리자 토큰만 호출할 수
 * 있다. 발급된 토큰은 대상 유저의 유저 토큰이므로, 프론트는 이를 저장해 유저 세션을 재현한다.
 */
@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
public class AdminImpersonationController {

  private final AdminImpersonationService adminImpersonationService;

  /** 대상 유저의 짧은 수명 access token 발급 (문의 재현용). 사유는 감사 로그에 남는다. */
  @PostMapping("/{userId}/impersonation-token")
  public ResponseEntity<AdminImpersonationTokenResponse> issueImpersonationToken(
      @AuthenticationPrincipal final Long adminId,
      @PathVariable final Long userId,
      @Valid @RequestBody final AdminImpersonationTokenRequest request) {
    return ResponseEntity.ok(
        adminImpersonationService.issueToken(adminId, userId, request.reason()));
  }
}
