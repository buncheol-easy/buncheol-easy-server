package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminAuthService;
import buncheoleasy.admin.dto.request.AdminLoginRequest;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 인증 API. 로그인은 SecurityConfig 에서 permitAll (그 외 /v1/admin/** 은 ROLE_ADMIN). */
@RestController
@RequestMapping("/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

  private final AdminAuthService adminAuthService;

  /** 관리자 ID/PW 로그인. 성공 시 관리자 access token(기본 12시간, refresh 없음)을 발급한다. */
  @PostMapping("/login")
  public ResponseEntity<AdminLoginResponse> login(
      @Valid @RequestBody final AdminLoginRequest request) {
    return ResponseEntity.ok(adminAuthService.login(request.loginId(), request.password()));
  }
}
