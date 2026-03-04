package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import buncheoleasy.auth.infrastructure.response.ErrorResponseWriter;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2LoginFailureHandler 단위 테스트")
class OAuth2LoginFailureHandlerTest {

  @Mock private ErrorResponseWriter errorResponseWriter;

  @Test
  void 로그인_실패시_표준_에러코드를_응답한다() throws Exception {
    // given
    OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(errorResponseWriter);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException exception = new AuthenticationException("failed") {};

    // when
    handler.onAuthenticationFailure(request, response, exception);

    // then
    ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
    then(errorResponseWriter)
        .should()
        .write(
            org.mockito.ArgumentMatchers.eq(request),
            org.mockito.ArgumentMatchers.eq(response),
            captor.capture());

    ProblemDetail problemDetail = captor.getValue();
    assertThat(problemDetail.getStatus())
        .isEqualTo(ErrorCode.AUTH_OAUTH2_LOGIN_FAILED.getHttpStatus().value());
    assertThat(problemDetail.getProperties().get("code"))
        .isEqualTo(ErrorCode.AUTH_OAUTH2_LOGIN_FAILED.getCode());
  }
}
