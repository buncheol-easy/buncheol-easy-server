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
    socialLoginService.logout(userId);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.expire().toString())
        .build();
  }
}
