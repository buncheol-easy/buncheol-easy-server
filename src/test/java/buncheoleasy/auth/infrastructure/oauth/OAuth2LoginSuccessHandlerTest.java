package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginCommand;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2LoginSuccessHandler 단위 테스트")
class OAuth2LoginSuccessHandlerTest {

  private static final String FRONTEND_CALLBACK_URL = "http://localhost:3000/login/callback";

  @Mock private OAuth2UserProfileExtractor profileExtractor;

  @Mock private SocialLoginService socialLoginService;

  @Mock private OAuth2AuthorizedClientService authorizedClientService;

  @Mock private KakaoApiClient kakaoApiClient;

  @Mock private OAuth2User principal;

  private final RefreshTokenCookieFactory refreshTokenCookieFactory =
      new RefreshTokenCookieFactory(1209600, false);

  private OAuth2LoginSuccessHandler createHandler() {
    return new OAuth2LoginSuccessHandler(
        List.of(profileExtractor),
        socialLoginService,
        refreshTokenCookieFactory,
        authorizedClientService,
        kakaoApiClient,
        FRONTEND_CALLBACK_URL);
  }

  @Nested
  @DisplayName("onAuthenticationSuccess 테스트")
  class OnAuthenticationSuccessTest {

    @Test
    void 프로필을_추출하고_로그인한_뒤_액세스토큰은_쿼리로_리프레시토큰은_쿠키로_내려준다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile =
          new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com");
      TokenPair tokenPair = new TokenPair("access", "refresh");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);
      given(socialLoginService.login(any(SocialLoginCommand.class))).willReturn(tokenPair);

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      ArgumentCaptor<SocialLoginCommand> captor =
          ArgumentCaptor.forClass(SocialLoginCommand.class);
      then(socialLoginService).should().login(captor.capture());
      assertThat(captor.getValue().provider()).isEqualTo("KAKAO");
      assertThat(captor.getValue().providerId()).isEqualTo("provider-id");
      assertThat(captor.getValue().email()).isEqualTo("test@example.com");
      assertThat(response.getStatus()).isEqualTo(302);
      assertThat(response.getRedirectedUrl())
          .isEqualTo(FRONTEND_CALLBACK_URL + "?accessToken=access");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
          .contains("refreshToken=refresh")
          .contains("HttpOnly")
          .contains("Path=/v1/auth")
          .contains("SameSite=Lax");
    }

    @Test
    void 카카오_토큰이_있으면_이름_전화번호_연령대_보강과_약관_동의_내역을_함께_전달한다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      given(principal.getName()).willReturn("provider-id");
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile =
          new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com");
      List<ServiceTermAgreement> terms =
          List.of(new ServiceTermAgreement("service_terms", true, Instant.now()));

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);

      OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
      given(authorizedClient.getAccessToken())
          .willReturn(
              new OAuth2AccessToken(
                  OAuth2AccessToken.TokenType.BEARER,
                  "kakao-access-token",
                  Instant.now(),
                  Instant.now().plusSeconds(3600)));
      given(authorizedClientService.loadAuthorizedClient("kakao", "provider-id"))
          .willReturn(authorizedClient);
      given(kakaoApiClient.getUserInfo("kakao-access-token"))
          .willReturn(new KakaoApiClient.KakaoUserInfo("김실명", "01012345678", "20~29", false));
      given(kakaoApiClient.getServiceTerms("kakao-access-token")).willReturn(terms);
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willReturn(new TokenPair("access", "refresh"));

      // when
      handler.onAuthenticationSuccess(
          new MockHttpServletRequest(), new MockHttpServletResponse(), authentication);

      // then
      ArgumentCaptor<SocialLoginCommand> captor =
          ArgumentCaptor.forClass(SocialLoginCommand.class);
      then(socialLoginService).should().login(captor.capture());
      assertThat(captor.getValue().name()).isEqualTo("김실명");
      assertThat(captor.getValue().phoneNumber()).isEqualTo("01012345678");
      assertThat(captor.getValue().ageRange()).isEqualTo("20~29");
      assertThat(captor.getValue().serviceTerms()).isEqualTo(terms);
    }

    @Test
    void 카카오_API_보강_조회가_실패해도_로그인은_계속_진행된다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      given(principal.getName()).willReturn("provider-id");
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile =
          new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);

      OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
      given(authorizedClient.getAccessToken())
          .willReturn(
              new OAuth2AccessToken(
                  OAuth2AccessToken.TokenType.BEARER,
                  "kakao-access-token",
                  Instant.now(),
                  Instant.now().plusSeconds(3600)));
      given(authorizedClientService.loadAuthorizedClient("kakao", "provider-id"))
          .willReturn(authorizedClient);
      given(kakaoApiClient.getUserInfo("kakao-access-token"))
          .willThrow(new RuntimeException("kapi 오류"));
      given(kakaoApiClient.getServiceTerms("kakao-access-token"))
          .willThrow(new RuntimeException("kapi 오류"));
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willReturn(new TokenPair("access", "refresh"));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      ArgumentCaptor<SocialLoginCommand> captor =
          ArgumentCaptor.forClass(SocialLoginCommand.class);
      then(socialLoginService).should().login(captor.capture());
      assertThat(captor.getValue().name()).isNull();
      assertThat(captor.getValue().serviceTerms()).isEmpty();
      assertThat(response.getStatus()).isEqualTo(302);
    }

    @Test
    void OAuth2AuthenticationToken이_아니면_예외가_발생한다() {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
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
      OAuth2LoginSuccessHandler handler = createHandler();
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
    void 소셜_이메일이_없으면_예외가_발생하고_리다이렉트도_쿠키도_내려보내지_않는다() {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile = new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", null);

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when & then
      assertThatThrownBy(
              () ->
                  handler.onAuthenticationSuccess(
                      new MockHttpServletRequest(), response, authentication))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);

      then(socialLoginService).should(never()).login(any(SocialLoginCommand.class));
      assertThat(response.getRedirectedUrl()).isNull();
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
  }
}
