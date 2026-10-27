package buncheoleasy.auth.presentation;

import buncheoleasy.auth.infrastructure.jwt.JwtAuthenticationFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 관리자 재현용 impersonation 토큰인지 본다. 로그아웃은 토큰의 userId 로 Redis refresh 를 삭제하므로, 재현 토큰으로 지우면 재현 대상 유저가 자기
 * 기기에서 강제 로그아웃된다 — 웹·앱 로그아웃 모두 이 확인 뒤에만 지운다.
 */
final class ImpersonationCheck {

  private ImpersonationCheck() {}

  // @AuthenticationPrincipal 과 동일하게 SecurityContextHolder 에서 읽는다 — 필터가 심은 IMPERSONATED 마커 확인.
  static boolean isImpersonated() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(JwtAuthenticationFilter.IMPERSONATED_AUTHORITY::equals);
  }
}
