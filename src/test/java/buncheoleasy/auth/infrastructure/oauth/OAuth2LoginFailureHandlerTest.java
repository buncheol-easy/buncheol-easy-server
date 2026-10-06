package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.auth.infrastructure.response.ErrorResponseWriter;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2LoginFailureHandler 단위 테스트")
class OAuth2LoginFailureHandlerTest {

  private static final String AGE_RANGE_CONSENT_CALLBACK_URL = "http://localhost:3000/upload";

  @Mock private ErrorResponseWriter errorResponseWriter;

  private OAuth2LoginFailureHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new OAuth2LoginFailureHandler(
            errorResponseWriter, new AgeRangeConsentRedirector(AGE_RANGE_CONSENT_CALLBACK_URL));
  }

  @Test
  void 로그인_실패시_표준_에러코드를_응답한다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException exception = new AuthenticationException("failed") {};

    // when
    handler.onAuthenticationFailure(request, response, exception);

    // then
    ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
    then(errorResponseWriter).should().write(eq(request), eq(response), captor.capture());

    ProblemDetail problemDetail = captor.getValue();
    assertThat(problemDetail.getStatus())
        .isEqualTo(ErrorCode.AUTH_OAUTH2_LOGIN_FAILED.getHttpStatus().value());
    assertThat(problemDetail.getProperties().get("code"))
        .isEqualTo(ErrorCode.AUTH_OAUTH2_LOGIN_FAILED.getCode());
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
      AuthenticationException exception =
          new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED));

      // when
      handler.onAuthenticationFailure(consentCallback(), response, exception);

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=cancelled");
      then(errorResponseWriter).should(never()).write(any(), any(), any());
    }

    @Test
    void 취소가_아닌_실패는_실패로_개최_화면에_돌려보낸다() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();
      AuthenticationException exception =
          new OAuth2AuthenticationException(new OAuth2Error("invalid_id_token"));

      // when
      handler.onAuthenticationFailure(consentCallback(), response, exception);

      // then
      assertThat(response.getRedirectedUrl())
          .isEqualTo(AGE_RANGE_CONSENT_CALLBACK_URL + "?ageRangeConsent=failed");
      then(errorResponseWriter).should(never()).write(any(), any(), any());
    }
  }
}
