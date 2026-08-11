package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminAuthService;
import buncheoleasy.admin.dto.request.AdminLoginRequest;
import buncheoleasy.admin.dto.response.AdminLoginResponse;
import buncheoleasy.global.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
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

  /**
   * 관리자 ID/PW 로그인. 성공 시 관리자 access token(기본 12시간, refresh 없음)을 발급한다.
   *
   * <p>클라이언트 IP 는 무차별 대입 방지의 제한 키로 쓴다 — 로그인 ID 를 바꿔가며 훑는 스프레이는 loginId 축 제한만으로는 잡히지 않는다.
   */
  @PostMapping("/login")
  public ResponseEntity<AdminLoginResponse> login(
      @Valid @RequestBody final AdminLoginRequest request, final HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        adminAuthService.login(
            request.loginId(), request.password(), ClientIpResolver.resolve(httpRequest)));
  }
}
