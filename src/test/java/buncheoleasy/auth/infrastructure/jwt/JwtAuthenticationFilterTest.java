package buncheoleasy.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter 단위 테스트")
class JwtAuthenticationFilterTest {

  @Mock private JwtTokenProvider jwtTokenProvider;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("토큰 추출/인증 테스트")
  class AuthenticationTest {

    @Test
    void Authorization_헤더가_없으면_인증없이_다음_필터로_진행한다() throws Exception {
      // given
      JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = new MockFilterChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      assertThat(request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE)).isNull();
    }

    @Test
    void 정상_Bearer_토큰이면_SecurityContext에_인증정보를_설정한다() throws Exception {
      // given
      JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer valid-token");
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = new MockFilterChain();
      given(jwtTokenProvider.parseAccessTokenClaims("valid-token"))
          .willReturn(new AccessTokenClaims(1L, null));

      // when
      filter.doFilter(request, response, chain);

      // then
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      assertThat(authentication).isNotNull();
      assertThat(authentication.getPrincipal()).isEqualTo(1L);
      // role claim 없는 유저 토큰은 ROLE_USER — 관리자 토큰과 id 공간이 겹쳐도 권한으로 구분된다.
      assertThat(authentication.getAuthorities())
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("ROLE_USER");
      assertThat(request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE)).isNull();
    }

    @Test
    void role_claim이_있으면_ROLE_권한을_부여한다() throws Exception {
      // given
      JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer admin-token");
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = new MockFilterChain();
      given(jwtTokenProvider.parseAccessTokenClaims("admin-token"))
          .willReturn(new AccessTokenClaims(7L, "ADMIN"));

      // when
      filter.doFilter(request, response, chain);

      // then
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      assertThat(authentication).isNotNull();
      assertThat(authentication.getPrincipal()).isEqualTo(7L);
      assertThat(authentication.getAuthorities())
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("ROLE_ADMIN");
    }

    @Test
    void Bearer_형식이_아니면_예외를_요청_속성에_저장한다() throws Exception {
      // given
      JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Token invalid");
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = new MockFilterChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      Object attribute = request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE);
      assertThat(attribute).isInstanceOf(BusinessException.class);
      assertThat(((BusinessException) attribute).getErrorCode())
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void Bearer_접두사_뒤에_토큰이_비어있으면_예외를_요청_속성에_저장한다() throws Exception {
      // given
      JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer   ");
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = new MockFilterChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      Object attribute = request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE);
      assertThat(attribute).isInstanceOf(BusinessException.class);
      assertThat(((BusinessException) attribute).getErrorCode())
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 토큰_파싱_중_예외가_발생하면_예외를_요청_속성에_저장한다() throws Exception {
      // given
      JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer expired-token");
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = new MockFilterChain();
      given(jwtTokenProvider.parseAccessTokenClaims("expired-token"))
          .willThrow(new BusinessException(ErrorCode.AUTH_EXPIRED_TOKEN));

      // when
      filter.doFilter(request, response, chain);

      // then
      Object attribute = request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE);
      assertThat(attribute).isInstanceOf(BusinessException.class);
      assertThat(((BusinessException) attribute).getErrorCode())
          .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }
}
