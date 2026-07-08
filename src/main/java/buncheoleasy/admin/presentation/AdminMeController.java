package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminMeQueryService;
import buncheoleasy.admin.dto.response.AdminMeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 본인 확인 API. {@code /v1/admin/**} 은 SecurityConfig 가 ROLE_ADMIN 을 강제한다. */
@RestController
@RequestMapping("/v1/admin/me")
@RequiredArgsConstructor
public class AdminMeController {

  private final AdminMeQueryService adminMeQueryService;

  /**
   * 현재 로그인한 관리자 정보 조회. admin 프론트가 저장된 토큰으로 세션이 유효한지 확인할 때 쓴다 — 200 이면 대시보드 진입, 401/403 이면 로그인
   * 화면으로. 토큰 claim(발급 시점)과 별개로 admins 테이블을 재조회해 확인 시점 기준으로 판정한다.
   */
  @GetMapping
  public ResponseEntity<AdminMeResponse> getMe(@AuthenticationPrincipal final Long adminId) {
    return ResponseEntity.ok(adminMeQueryService.getMe(adminId));
  }
}
