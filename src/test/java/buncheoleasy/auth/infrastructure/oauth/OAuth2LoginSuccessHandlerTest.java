package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.AppLoginCode;
import buncheoleasy.auth.application.AppLoginService;
import buncheoleasy.auth.application.SocialLoginCommand;
import buncheoleasy.auth.application.SocialLoginResult;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.AgeRangeRefreshResult;
import buncheoleasy.user.domain.SocialProvider;
import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("OAuth2LoginSuccessHandler 단위 테스트")
class OAuth2LoginSuccessHandlerTest {

  private static final String FRONTEND_CALLBACK_URL = "http://localhost:3000/login/callback";
  private static final String REFRESH_COOKIE_PATH = "/api/backend/v1/auth";
  private static final String AGE_RANGE_CONSENT_CALLBACK_URL = "http://localhost:3000/upload";
  private static final String APP_CALLBACK_URL = "buncheoleasy://auth";
  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

  @Mock private OAuth2UserProfileExtractor profileExtractor;

  @Mock private SocialLoginService socialLoginService;

  @Mock private OAuth2AuthorizedClientService authorizedClientService;

  @Mock private KakaoApiClient kakaoApiClient;

  @Mock private OAuth2User principal;

  @Mock private AppLoginService appLoginService;

  private final RefreshTokenCookieFactory refreshTokenCookieFactory =
      new RefreshTokenCookieFactory(1209600, false, REFRESH_COOKIE_PATH);

  private OAuth2LoginSuccessHandler createHandler() {
    return createHandler(FRONTEND_CALLBACK_URL);
  }

  private OAuth2LoginSuccessHandler createHandler(final String loginCallbackUrl) {
    return createHandler(loginCallbackUrl, APP_CALLBACK_URL);
  }

  private OAuth2LoginSuccessHandler createHandler(
      final String loginCallbackUrl, final String appCallbackUrl) {
    return new OAuth2LoginSuccessHandler(
        List.of(profileExtractor),
        socialLoginService,
        refreshTokenCookieFactory,
        authorizedClientService,
        kakaoApiClient,
        new AgeRangeConsentRedirector(AGE_RANGE_CONSENT_CALLBACK_URL),
        new LoginFailureRedirector(loginCallbackUrl),
        appLoginService,
        new AppLoginRedirector(appCallbackUrl),
        loginCallbackUrl);
  }

  private static MockHttpServletRequest appLoginCallback(final String state) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
    request.setParameter("state", state);
    return request;
  }

  private OAuth2AuthenticationToken givenKakaoProfile() {
    given(profileExtractor.supports("kakao")).willReturn(true);
    given(profileExtractor.extract(principal))
        .willReturn(new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com"));
    return new OAuth2AuthenticationToken(
        principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
  }

  @Nested
  @DisplayName("앱 로그인 콜백 (state = app.<challenge>.<무작위>)")
  class AppLoginCallbackTest {

    @Test
    void 쿠키와_토큰_없이_일회용_코드만_앱_주소로_돌려준다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication = givenKakaoProfile();
      given(appLoginService.issueLoginCode(any(SocialLoginCommand.class), eq(CHALLENGE)))
          .willReturn(new AppLoginCode("one-time-code", false));
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(
          appLoginCallback("app." + CHALLENGE + ".random"), response, authentication);

      // then
      assertThat(response.getStatus()).isEqualTo(302);
      assertThat(response.getRedirectedUrl()).isEqualTo(APP_CALLBACK_URL + "?code=one-time-code");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
      then(socialLoginService).should(never()).login(any());
    }

    @Test
    void 가입_처리는_웹과_같은_명령으로_한다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication = givenKakaoProfile();
      given(appLoginService.issueLoginCode(any(SocialLoginCommand.class), eq(CHALLENGE)))
          .willReturn(new AppLoginCode("one-time-code", false));

      // when
      handler.onAuthenticationSuccess(
          appLoginCallback("app." + CHALLENGE + ".random"),
          new MockHttpServletResponse(),
          authentication);

      // then
      ArgumentCaptor<SocialLoginCommand> captor =
          ArgumentCaptor.forClass(SocialLoginCommand.class);
      then(appLoginService).should().issueLoginCode(captor.capture(), eq(CHALLENGE));
      assertThat(captor.getValue().provider()).isEqualTo("KAKAO");
      assertThat(captor.getValue().providerId()).isEqualTo("provider-id");
      assertThat(captor.getValue().email()).isEqualTo("test@example.com");
    }

    @Test
    void 새_가입이면_signup_1_을_붙인다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication = givenKakaoProfile();
      given(appLoginService.issueLoginCode(any(SocialLoginCommand.class), eq(CHALLENGE)))
          .willReturn(new AppLoginCode("one-time-code", true));
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(
          appLoginCallback("app." + CHALLENGE + ".random"), response, authentication);

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(APP_CALLBACK_URL + "?code=one-time-code&signup=1");
    }

    @Test
    void 이메일이_없어_가입하지_못하면_앱_주소로_email_required() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication = givenKakaoProfile();
      given(appLoginService.issueLoginCode(any(SocialLoginCommand.class), eq(CHALLENGE)))
          .willThrow(new BusinessException(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED));
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(
          appLoginCallback("app." + CHALLENGE + ".random"), response, authentication);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(APP_CALLBACK_URL + "?error=email_required");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void 그_밖의_실패는_앱_주소로_failed() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication = givenKakaoProfile();
      given(appLoginService.issueLoginCode(any(SocialLoginCommand.class), eq(CHALLENGE)))
          .willThrow(new IllegalStateException("redis down"));
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(
          appLoginCallback("app." + CHALLENGE + ".random"), response, authentication);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(APP_CALLBACK_URL + "?error=failed");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void 앱_로그인이_꺼져_있으면_가입도_코드도_없이_웹_실패_화면으로_보낸다() throws Exception {
      // given — 켜진 사이 시작했다가 꺼진 뒤 돌아온 콜백
      OAuth2LoginSuccessHandler handler = createHandler(FRONTEND_CALLBACK_URL, "");
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(
          appLoginCallback("app." + CHALLENGE + ".random"), response, authentication);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_CALLBACK_URL + "?error=failed");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
      then(appLoginService).shouldHaveNoInteractions();
      then(socialLoginService).shouldHaveNoInteractions();
    }

    @Test
    void state_의_challenge_모양이_다르면_코드를_만들지_않고_failed() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(appLoginCallback("app.short.random"), response, authentication);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(APP_CALLBACK_URL + "?error=failed");
      then(appLoginService).shouldHaveNoInteractions();
      then(socialLoginService).shouldHaveNoInteractions();
    }
  }

  /** OutputCaptureExtension 은 앞선 테스트의 출력까지 돌려주므로, 표지 줄을 찍고 그 뒤만 본다. */
  private static String markLogs() {
    String mark = "test-mark-" + UUID.randomUUID();
    System.out.println(mark);
    return mark;
  }

  private static String logsAfter(final CapturedOutput output, final String mark) {
    String all = output.getAll();
    return all.substring(all.lastIndexOf(mark) + mark.length());
  }

  private void givenKakaoAccessToken() {
    given(principal.getName()).willReturn("provider-id");
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
  }

  @Nested
  @DisplayName("onAuthenticationSuccess 테스트")
  class OnAuthenticationSuccessTest {

    @Test
    void 프로필을_추출하고_로그인한_뒤_액세스토큰은_프래그먼트로_리프레시토큰은_쿠키로_내려준다() throws Exception {
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
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willReturn(new SocialLoginResult(tokenPair, false));

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
          .isEqualTo(FRONTEND_CALLBACK_URL + "#accessToken=access");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
          .contains("refreshToken=refresh")
          .contains("HttpOnly")
          .contains("Path=" + REFRESH_COOKIE_PATH)
          .contains("SameSite=Lax");
    }

    @Test
    void 이번_로그인으로_계정이_생겼으면_프래그먼트에_가입_표시를_붙인다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile =
          new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willReturn(new SocialLoginResult(new TokenPair("access", "refresh"), true));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(FRONTEND_CALLBACK_URL + "#accessToken=access&signup=1");
    }

    @Test
    void JWT_는_인코딩_없이_프래그먼트에_원문_그대로_보존된다() throws Exception {
      // base64url([A-Za-z0-9_-]) + '.' 은 프래그먼트에서 이스케이프가 불필요하다는 전제를 고정한다.
      // given
      OAuth2LoginSuccessHandler handler = createHandler(FRONTEND_CALLBACK_URL);
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile =
          new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com");
      String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3IiwiZXhwIjo5OTk5fQ.s1gn-4tur3_Xyz";

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willReturn(new SocialLoginResult(new TokenPair(jwt, "refresh"), false));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(FRONTEND_CALLBACK_URL + "#accessToken=" + jwt);
    }

    @Test
    void 콜백_URL에_쿼리가_있어도_프래그먼트는_쿼리_뒤에_붙는다() throws Exception {
      // FRONTEND_LOGIN_CALLBACK_URL 은 env 라 쿼리가 붙은 값이 들어올 수 있다. UriComponentsBuilder 의
      // 조립 순서(?query 다음 #fragment)에 의존하므로 계약으로 고정한다.
      // given
      OAuth2LoginSuccessHandler handler =
          createHandler("http://localhost:3000/login/callback?foo=bar");
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile =
          new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willReturn(new SocialLoginResult(new TokenPair("access", "refresh"), false));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo("http://localhost:3000/login/callback?foo=bar#accessToken=access");
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
          .willReturn(new SocialLoginResult(new TokenPair("access", "refresh"), false));

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
          .willReturn(new SocialLoginResult(new TokenPair("access", "refresh"), false));

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
    void 프로필_추출이_실패하면_오류_화면_대신_실패로_로그인_콜백에_돌려보낸다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal))
          .willThrow(new RuntimeException("broken principal"));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_CALLBACK_URL + "?error=failed");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
      then(socialLoginService).should(never()).login(any(SocialLoginCommand.class));
    }

    @Test
    void 새_회원인데_카카오가_이메일을_주지_않으면_이메일_필요로_돌려보내고_쿠키를_내려보내지_않는다(
        final CapturedOutput output) throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile = new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", null);

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willThrow(new BusinessException(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED));

      MockHttpServletResponse response = new MockHttpServletResponse();
      String mark = markLogs();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      assertThat(response.getStatus()).isEqualTo(302);
      assertThat(response.getRedirectedUrl())
          .isEqualTo(FRONTEND_CALLBACK_URL + "?error=email_required");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
      String logs = logsAfter(output, mark);
      assertThat(logs)
          .contains("WARN")
          .contains("소셜 로그인 거절: 사유=email_required")
          .doesNotContain("ERROR")
          .doesNotContain("provider-id");
    }

    @Test
    void 기존_회원이면_카카오가_이메일을_주지_않아도_평소처럼_로그인된다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
      OAuth2UserProfile profile = new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", null);

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal)).willReturn(profile);
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willReturn(new SocialLoginResult(new TokenPair("access", "refresh"), false));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      ArgumentCaptor<SocialLoginCommand> captor =
          ArgumentCaptor.forClass(SocialLoginCommand.class);
      then(socialLoginService).should().login(captor.capture());
      assertThat(captor.getValue().email()).isNull();
      assertThat(response.getRedirectedUrl())
          .isEqualTo(FRONTEND_CALLBACK_URL + "#accessToken=access");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("refreshToken=refresh");
    }

    @Test
    void 로그인_처리_중_예상_못_한_예외가_나면_실패로_돌려보내고_스택과_함께_ERROR_로_남긴다(
        final CapturedOutput output) throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal))
          .willReturn(
              new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com"));
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willThrow(new IllegalStateException("DB 오류"));

      MockHttpServletResponse response = new MockHttpServletResponse();
      String mark = markLogs();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_CALLBACK_URL + "?error=failed");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
      String logs = logsAfter(output, mark);
      assertThat(logs)
          .contains("ERROR")
          .contains("소셜 로그인 처리 실패: 사유=failed")
          .contains("java.lang.IllegalStateException: DB 오류")
          .contains("\tat ")
          .doesNotContain("provider-id")
          .doesNotContain("test@example.com");
    }

    @Test
    void 이메일_형식_불합격_같은_다른_업무_예외는_이메일_필요가_아니라_실패로_돌려보낸다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      OAuth2AuthenticationToken authentication =
          new OAuth2AuthenticationToken(
              principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");

      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal))
          .willReturn(new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "o'neil@kakao"));
      given(socialLoginService.login(any(SocialLoginCommand.class)))
          .willThrow(new BusinessException(ErrorCode.USER_EMAIL_FORMAT_INVALID));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_CALLBACK_URL + "?error=failed");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
  }

  @Nested
  @DisplayName("연령대 추가 동의 콜백 테스트")
  class AgeRangeConsentCallbackTest {

    private OAuth2AuthenticationToken authentication() {
      return new OAuth2AuthenticationToken(
          principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "kakao");
    }

    private MockHttpServletRequest consentCallback() {
      MockHttpServletRequest request =
          new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
      request.setParameter(
          "state", KakaoAuthorizationRequestResolver.AGE_RANGE_CONSENT_STATE_PREFIX + "state");
      return request;
    }

    private void givenKakaoProfile() {
      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal))
          .willReturn(
              new OAuth2UserProfile(SocialProvider.KAKAO, "provider-id", "test@example.com"));
    }

    @Test
    void 연령대만_갱신하고_토큰_없이_개최_화면으로_동의_결과를_돌려보낸다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      givenKakaoProfile();
      givenKakaoAccessToken();
      given(kakaoApiClient.getUserInfo("kakao-access-token"))
          .willReturn(new KakaoApiClient.KakaoUserInfo("김실명", "01012345678", "20~29", false));
      given(socialLoginService.refreshAgeRange("KAKAO", "provider-id", "20~29", false))
          .willReturn(AgeRangeRefreshResult.PRESENT);

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(consentCallback(), response, authentication());

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=agreed");
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
      then(socialLoginService).should(never()).login(any(SocialLoginCommand.class));
      then(kakaoApiClient).should(never()).getServiceTerms(anyString());
    }

    @Test
    void 반영_뒤에도_연령대가_없으면_실패로_돌려보낸다(final CapturedOutput output) throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      givenKakaoProfile();
      givenKakaoAccessToken();
      given(kakaoApiClient.getUserInfo("kakao-access-token"))
          .willReturn(new KakaoApiClient.KakaoUserInfo(null, null, null, false));
      given(socialLoginService.refreshAgeRange("KAKAO", "provider-id", null, false))
          .willReturn(AgeRangeRefreshResult.ABSENT);

      MockHttpServletResponse response = new MockHttpServletResponse();
      String mark = markLogs();

      // when
      handler.onAuthenticationSuccess(consentCallback(), response, authentication());

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
      assertThat(logsAfter(output, mark))
          .contains("연령대 추가 동의 후에도 연령대가 없음: needsAgreement=false")
          .doesNotContain("provider-id");
    }

    @Test
    void 연령대_동의_철회_신호는_그대로_전달하고_실패로_돌려보낸다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      givenKakaoProfile();
      givenKakaoAccessToken();
      given(kakaoApiClient.getUserInfo("kakao-access-token"))
          .willReturn(new KakaoApiClient.KakaoUserInfo(null, null, null, true));
      given(socialLoginService.refreshAgeRange("KAKAO", "provider-id", null, true))
          .willReturn(AgeRangeRefreshResult.ABSENT);

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(consentCallback(), response, authentication());

      // then
      then(socialLoginService).should().refreshAgeRange("KAKAO", "provider-id", null, true);
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
    }

    @Test
    void 연령대_반영_중_예외가_나도_오류_페이지_대신_실패로_돌려보낸다(final CapturedOutput output) throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      givenKakaoProfile();
      givenKakaoAccessToken();
      given(kakaoApiClient.getUserInfo("kakao-access-token"))
          .willReturn(new KakaoApiClient.KakaoUserInfo(null, null, "20~29", false));
      given(socialLoginService.refreshAgeRange("KAKAO", "provider-id", "20~29", false))
          .willThrow(new IllegalStateException("DB 오류"));

      MockHttpServletResponse response = new MockHttpServletResponse();
      String mark = markLogs();

      // when
      handler.onAuthenticationSuccess(consentCallback(), response, authentication());

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
      assertThat(logsAfter(output, mark))
          .contains("연령대 추가 동의 반영 실패: reason=DB 오류")
          .doesNotContain("provider-id");
    }

    @Test
    void 가입한_회원이_없는_카카오_계정이면_실패로_돌려보낸다(final CapturedOutput output) throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      givenKakaoProfile();
      givenKakaoAccessToken();
      given(kakaoApiClient.getUserInfo("kakao-access-token"))
          .willReturn(new KakaoApiClient.KakaoUserInfo(null, null, "20~29", false));
      given(socialLoginService.refreshAgeRange("KAKAO", "provider-id", "20~29", false))
          .willReturn(AgeRangeRefreshResult.NO_MEMBER);

      MockHttpServletResponse response = new MockHttpServletResponse();
      String mark = markLogs();

      // when
      handler.onAuthenticationSuccess(consentCallback(), response, authentication());

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
      then(socialLoginService).should(never()).login(any(SocialLoginCommand.class));
      assertThat(logsAfter(output, mark))
          .contains("연령대 추가 동의 계정으로 가입한 회원이 없음")
          .doesNotContain("provider-id");
    }

    @Test
    void 카카오_사용자_정보_조회가_실패하면_갱신하지_않고_실패로_돌려보낸다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      givenKakaoProfile();
      givenKakaoAccessToken();
      given(kakaoApiClient.getUserInfo("kakao-access-token"))
          .willThrow(new RuntimeException("kapi 오류"));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(consentCallback(), response, authentication());

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
      then(socialLoginService)
          .should(never())
          .refreshAgeRange(anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    void 프로필_추출이_실패해도_오류_페이지_대신_실패로_돌려보낸다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      given(profileExtractor.supports("kakao")).willReturn(true);
      given(profileExtractor.extract(principal))
          .willThrow(new RuntimeException("broken principal"));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(consentCallback(), response, authentication());

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
      then(socialLoginService)
          .should(never())
          .refreshAgeRange(anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    void 카카오_access_token_을_찾지_못하면_실패로_돌려보낸다() throws Exception {
      // given
      OAuth2LoginSuccessHandler handler = createHandler();
      givenKakaoProfile();
      given(principal.getName()).willReturn("provider-id");
      given(authorizedClientService.loadAuthorizedClient("kakao", "provider-id")).willReturn(null);

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationSuccess(consentCallback(), response, authentication());

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
      then(kakaoApiClient).should(never()).getUserInfo(anyString());
    }
  }
}
