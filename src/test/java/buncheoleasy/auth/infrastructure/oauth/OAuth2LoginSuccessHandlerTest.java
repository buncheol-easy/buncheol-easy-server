package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.infrastructure.response.TokenResponseWriter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2LoginSuccessHandler 단위 테스트")
class OAuth2LoginSuccessHandlerTest {

  @Mock private OAuth2UserProfileExtractor profileExtractor;

  @Mock private SocialLoginService socialLoginService;

  @Mock private TokenResponseWriter tokenResponseWriter;

  @Mock private OAuth2User principal;

  @Nested
  @DisplayName("onAuthenticationSuccess 테스트")
  class OnAuthenticationSuccessTest {

    @Test
    void 프로필을_추출하고_로그인한_뒤_토큰을_응답으로_작성한다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler =
          new OAuth2LoginSuccessHandler(
              List.of(profileExtractor), socialLoginService, tokenResponseWriter);
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile =
          new OAuth2UserProfile(
              SocialProvider.KAKAO, "provider-id", "test@example.com");
      TokenPair tokenPair = new TokenPair("access", "refresh");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);
      given(socialLoginService.login("KAKAO", "provider-id", "test@example.com"))
          .willReturn(tokenPair);

      // when
      handler.onAuthenticationSuccess(
          new MockHttpServletRequest(), new MockHttpServletResponse(), authentication);

      // then
      then(socialLoginService).should().login("KAKAO", "provider-id", "test@example.com");
      then(tokenResponseWriter)
          .should()
          .write(any(), eq(tokenPair));
    }

    @Test
    void OAuth2AuthenticationToken이_아니면_예외가_발생한다() {
      // given
      OAuth2LoginSuccessHandler handler =
          new OAuth2LoginSuccessHandler(
              List.of(profileExtractor), socialLoginService, tokenResponseWriter);
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken("user", null);

      // when & then
      assertThatThrownBy(
              () ->
                  handler.onAuthenticationSuccess(
                      new MockHttpServletRequest(), new MockHttpServletResponse(), authentication))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_UNSUPPORTED_AUTHENTICATION);
    }

    @Test
    void 프로필_추출_중_런타임_예외가_발생하면_지원하지_않는_제공자로_처리한다() {
      // given
      OAuth2LoginSuccessHandler handler =
          new OAuth2LoginSuccessHandler(
              List.of(profileExtractor), socialLoginService, tokenResponseWriter);
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal))
          .willThrow(new RuntimeException("broken principal"));

      // when & then
      assertThatThrownBy(
              () ->
                  handler.onAuthenticationSuccess(
                      new MockHttpServletRequest(), new MockHttpServletResponse(), authentication))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_SOCIAL_PROVIDER_UNSUPPORTED);
    }

    @Test
    void 소셜_이메일이_없으면_예외가_발생한다() {
      // given
      OAuth2LoginSuccessHandler handler =
          new OAuth2LoginSuccessHandler(
              List.of(profileExtractor), socialLoginService, tokenResponseWriter);
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile =
          new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", null);

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);

      // when & then
      assertThatThrownBy(
              () ->
                  handler.onAuthenticationSuccess(
                      new MockHttpServletRequest(), new MockHttpServletResponse(), authentication))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);

      then(socialLoginService)
          .should(never())
          .login(
              anyString(),
              anyString(),
              any());
      then(tokenResponseWriter).shouldHaveNoInteractions();
    }
  }
}
