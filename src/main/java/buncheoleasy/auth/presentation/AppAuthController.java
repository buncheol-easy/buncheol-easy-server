package buncheoleasy.auth.presentation;

import buncheoleasy.auth.application.AppLoginService;
import buncheoleasy.auth.dto.request.AppReissueRequest;
import buncheoleasy.auth.dto.request.AppTokenRequest;
import buncheoleasy.auth.dto.request.AppleLoginRequest;
import buncheoleasy.auth.dto.response.AppLoginResponse;
import buncheoleasy.auth.dto.response.AppTokenResponse;
import buncheoleasy.global.presentation.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네이티브 앱 로그인. 토큰은 쿠키가 아니라 본문으로 주고받고, refresh 는 웹과 다른 칸(앱 칸)에 저장한다 — 웹 엔드포인트({@code
 * /v1/auth/reissue-token}·{@code /v1/auth/logout})의 동작은 그대로다.
 */
@RestController
@RequestMapping("/v1/auth/app")
@RequiredArgsConstructor
public class AppAuthController {

  private final AppLoginService appLoginService;

  @PostMapping("/token")
  public ResponseEntity<AppLoginResponse> exchangeCode(
      @Valid @RequestBody final AppTokenRequest request) {
    return ResponseEntity.ok(
        AppLoginResponse.from(appLoginService.exchangeCode(request.code(), request.verifier())));
  }

  @PostMapping("/apple")
  public ResponseEntity<AppLoginResponse> loginWithApple(
      @Valid @RequestBody final AppleLoginRequest request, final HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        AppLoginResponse.from(
            appLoginService.loginWithApple(
                request.identityToken(),
                request.rawNonce(),
                request.fullName(),
                ClientIpResolver.resolve(httpRequest))));
  }

  @PostMapping("/reissue")
  public ResponseEntity<AppTokenResponse> reissue(
      @Valid @RequestBody final AppReissueRequest request) {
    return ResponseEntity.ok(
        AppTokenResponse.from(appLoginService.reissueTokens(request.refreshToken())));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal final Long userId) {
    if (!ImpersonationCheck.isImpersonated()) {
      appLoginService.logout(userId);
    }
    return ResponseEntity.noContent().build();
  }
}
