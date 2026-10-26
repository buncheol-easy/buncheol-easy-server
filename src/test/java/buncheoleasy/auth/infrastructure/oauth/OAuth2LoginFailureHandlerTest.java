package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("OAuth2LoginFailureHandler 단위 테스트")
class OAuth2LoginFailureHandlerTest {

  private static final String LOGIN_CALLBACK_URL = "http://localhost:3000/login/callback";
  private static final String AGE_RANGE_CONSENT_CALLBACK_URL = "http://localhost:3000/upload";
  private static final String APP_CALLBACK_URL = "buncheoleasy://auth";

  private OAuth2LoginFailureHandler handler;

  @BeforeEach
  void setUp() {
    handler = createHandler(LOGIN_CALLBACK_URL);
  }

  private static OAuth2LoginFailureHandler createHandler(final String loginCallbackUrl) {
    return new OAuth2LoginFailureHandler(
        new LoginFailureRedirector(loginCallbackUrl),
        new AgeRangeConsentRedirector(AGE_RANGE_CONSENT_CALLBACK_URL),
        new AppLoginRedirector(APP_CALLBACK_URL));
  }

  private static OAuth2AuthenticationException oauth2Failure(final String errorCode) {
    return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
  }

  /** 실패 응답은 돌려보내기만 한다 — 오류 JSON 본문도, 리프레시 쿠키도 없다. */
  private static void assertRedirectOnly(final MockHttpServletResponse response) throws Exception {
    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getContentAsString()).isEmpty();
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Nested
  @DisplayName("앱 로그인 콜백 — 웹 화면이 아니라 앱 주소로")
  class AppLoginCallbackTest {

    private MockHttpServletRequest appLoginCallback() {
      MockHttpServletRequest request =
          new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
      request.setParameter("state", "app.E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM.random");
      return request;
    }

    @Test
    void 사용자가_카카오_동의를_취소하면_앱_주소로_cancelled() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationFailure(
          appLoginCallback(), response, oauth2Failure(OAuth2ErrorCodes.ACCESS_DENIED));

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(APP_CALLBACK_URL + "?error=cancelled");
      assertRedirectOnly(response);
    }

    @Test
    void 그_밖의_실패는_앱_주소로_failed() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationFailure(
          appLoginCallback(), response, oauth2Failure("invalid_token_response"));

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(APP_CALLBACK_URL + "?error=failed");
      assertRedirectOnly(response);
    }
  }

  @Nested
  @DisplayName("일반 로그인 콜백")
  class LoginCallbackTest {

    private MockHttpServletRequest loginCallback() {
      MockHttpServletRequest request =
          new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
      request.setParameter("state", "state");
      return request;
    }

    @Test
    void 사용자가_카카오_동의를_취소하면_취소됨으로_로그인_콜백에_돌려보낸다() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationFailure(
          loginCallback(), response, oauth2Failure(OAuth2ErrorCodes.ACCESS_DENIED));

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_CALLBACK_URL + "?error=cancelled");
      assertRedirectOnly(response);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "authorization_request_not_found",
          "invalid_request",
          "invalid_token_response",
          "invalid_id_token"
        })
    void 취소가_아닌_OAuth2_실패는_실패로_로그인_콜백에_돌려보낸다(final String errorCode) throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      handler.onAuthenticationFailure(loginCallback(), response, oauth2Failure(errorCode));

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_CALLBACK_URL + "?error=failed");
      assertRedirectOnly(response);
    }

    @Test
    void OAuth2_오류가_아닌_인증_실패도_실패로_돌려보낸다() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();
      AuthenticationException exception = new AuthenticationException("failed") {};

      // when
      handler.onAuthenticationFailure(loginCallback(), response, exception);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_CALLBACK_URL + "?error=failed");
      assertRedirectOnly(response);
    }

    @Test
    void 카카오가_보낸_오류_설명은_돌려보내는_주소에도_로그에도_싣지_않는다(final CapturedOutput output)
        throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();
      AuthenticationException exception =
          new OAuth2AuthenticationException(
              new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, "<b>description-marker</b>", null));

      // when
      handler.onAuthenticationFailure(loginCallback(), response, exception);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_CALLBACK_URL + "?error=cancelled");
      assertThat(output.getAll())
          .contains("소셜 로그인 실패: 사유=cancelled, 원인코드=access_denied")
          .doesNotContain("description-marker");
    }

    @Test
    void 코드_모양이_아닌_오류_코드는_로그에_그대로_남기지_않는다(final CapturedOutput output)
        throws Exception {
      // given: 저장된 인가 요청이 있으면 콜백의 error 쿼리 값이 그대로 오류 코드가 된다
      MockHttpServletResponse response = new MockHttpServletResponse();
      AuthenticationException exception =
          oauth2Failure("x\nFAKE-INJECTED-LINE ERROR something");

      // when
      handler.onAuthenticationFailure(loginCallback(), response, exception);

      // then
      assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_CALLBACK_URL + "?error=failed");
      assertThat(output.getAll())
          .contains("소셜 로그인 실패: 사유=failed, 원인코드=malformed")
          .doesNotContain("FAKE-INJECTED-LINE");
    }

    @Test
    void 콜백_URL에_쿼리가_있어도_사유는_뒤에_이어_붙는다() throws Exception {
      // given
      OAuth2LoginFailureHandler queryHandler =
          createHandler("http://localhost:3000/login/callback?foo=bar");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      queryHandler.onAuthenticationFailure(
          loginCallback(), response, oauth2Failure("authorization_request_not_found"));

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo("http://localhost:3000/login/callback?foo=bar&error=failed");
    }
  }

  @Nested
  @DisplayName("연령대 추가 동의 콜백")
  class AgeRangeConsentCallbackTest {

    private MockHttpServletRequest consentCallback() {
      MockHttpServletRequest request =
          new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
      request.setParameter(
          "state", KakaoAuthorizationRequestResolver.AGE_RANGE_CONSENT_STATE_PREFIX + "state");
      return request;
    }

    @Test
    void 사용자가_동의를_취소하면_오류_응답_대신_취소됨으로_개최_화면에_돌려보낸다() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();
      AuthenticationException exception = oauth2Failure(OAuth2ErrorCodes.ACCESS_DENIED);

      // when
      handler.onAuthenticationFailure(consentCallback(), response, exception);

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=cancelled");
      assertRedirectOnly(response);
    }

    @Test
    void 취소가_아닌_실패는_실패로_개최_화면에_돌려보낸다() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();
      AuthenticationException exception = oauth2Failure("invalid_id_token");

      // when
      handler.onAuthenticationFailure(consentCallback(), response, exception);

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
      assertRedirectOnly(response);
    }
  }
}
