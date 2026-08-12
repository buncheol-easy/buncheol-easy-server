package buncheoleasy.auth.infrastructure.jwt;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  public static final String EXCEPTION_ATTRIBUTE = "exception";

  /**
   * 관리자 재현용 impersonation 토큰에 부여되는 마커 권한(role 아님). 유저 API 는 그대로 통과시키되, refresh 세션을 지우는 로그아웃처럼
   * 재현 대상 유저에게 사고를 낼 수 있는 동작을 이 마커로 구분해 막는다.
   */
  public static final String IMPERSONATED_AUTHORITY = "IMPERSONATED";

  private final JwtTokenProvider jwtTokenProvider;

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    try {
      final String accessToken = extractAccessToken(request);
      if (accessToken != null) {
        authenticateWithToken(accessToken);
      }
    } catch (final BusinessException exception) {
      handleException(request, exception);
    }

    filterChain.doFilter(request, response);
  }

  /** 요청 헤더에서 액세스 토큰 추출 */
  private String extractAccessToken(final HttpServletRequest request) {
    final String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

    if (!StringUtils.hasText(bearerToken)) {
      return null;
    }

    validateBearerFormat(bearerToken);

    final String token = bearerToken.substring(BEARER_PREFIX.length()).trim();
    validateTokenPresence(token);

    return token;
  }

  private void validateBearerFormat(final String bearerToken) {
    if (!bearerToken.startsWith(BEARER_PREFIX)) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
    }
  }

  private void validateTokenPresence(final String token) {
    if (!StringUtils.hasText(token)) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
    }
  }

  private void authenticateWithToken(final String accessToken) {
    final AccessTokenClaims claims = jwtTokenProvider.parseAccessTokenClaims(accessToken);

    final Authentication authentication =
        new UsernamePasswordAuthenticationToken(claims.userId(), null, toAuthorities(claims));

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  // role claim 이 없으면 유저 토큰(ROLE_USER), 있으면 관리자 토큰(ROLE_ADMIN 등)이다. 유저와 관리자의 id 공간이
  // 겹칠 수 있어 두 권한을 겸하지 않는다 — 관리자 토큰으로 유저 API(hasRole USER)에 접근할 수 없고 그 반대도 같다.
  // 서명 검증을 통과한 토큰만 오므로 claim 값 자체는 신뢰한다.
  private List<GrantedAuthority> toAuthorities(final AccessTokenClaims claims) {
    final SimpleGrantedAuthority role =
        new SimpleGrantedAuthority(claims.role() == null ? "ROLE_USER" : "ROLE_" + claims.role());
    if (claims.impersonated()) {
      return List.of(role, new SimpleGrantedAuthority(IMPERSONATED_AUTHORITY));
    }
    return List.of(role);
  }

  private void handleException(
      final HttpServletRequest request, final BusinessException exception) {
    request.setAttribute(EXCEPTION_ATTRIBUTE, exception);
  }
}
