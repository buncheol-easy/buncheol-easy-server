package buncheoleasy.auth.presentation;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.dto.response.AccessTokenResponse;
import buncheoleasy.auth.infrastructure.oauth.RefreshTokenCookieFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final SocialLoginService socialLoginService;
  private final RefreshTokenCookieFactory refreshTokenCookieFactory;

  @PostMapping("/reissue-token")
  public ResponseEntity<AccessTokenResponse> reissueToken(
      @CookieValue(RefreshTokenCookieFactory.COOKIE_NAME) final String refreshToken) {
    TokenPair token = socialLoginService.reissueTokens(refreshToken);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshTokenCookieFactory.create(token.refreshToken()).toString())
        .body(new AccessTokenResponse(token.accessToken()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal final Long userId) {
    // 관리자 재현용 impersonation 토큰이면 refresh 세션을 지우지 않는다 — 로그아웃은 토큰의 userId 로 Redis
    // refresh 를 삭제하므로, 그대로 두면 재현 대상 유저가 자기 기기에서 강제 로그아웃된다. 재현 세션은 토큰 만료(기본
    // 15분)로 끝나고, 클라이언트는 로컬 토큰만 폐기하면 된다.
    if (!ImpersonationCheck.isImpersonated()) {
      socialLoginService.logout(userId);
    }
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.expire().toString())
        .build();
  }
}
