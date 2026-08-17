package buncheoleasy.auth.presentation;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.dto.response.AccessTokenResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtAuthenticationFilter;
import buncheoleasy.auth.infrastructure.oauth.RefreshTokenCookieFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
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
    if (!isImpersonated()) {
      socialLoginService.logout(userId);
    }
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.expire().toString())
        .build();
  }

  // @AuthenticationPrincipal 과 동일하게 SecurityContextHolder 에서 읽는다 — 필터가 심은 IMPERSONATED 마커 확인.
  private boolean isImpersonated() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(JwtAuthenticationFilter.IMPERSONATED_AUTHORITY::equals);
  }
}
