package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.auth.infrastructure.response.ErrorResponseWriter;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  private final ErrorResponseWriter errorResponseWriter;
  private final AgeRangeConsentRedirector ageRangeConsentRedirector;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    if (KakaoAuthorizationRequestResolver.isAgeRangeConsentCallback(request)) {
      ageRangeConsentRedirector.redirect(response, toAgeRangeConsentResult(exception));
      return;
    }
    log.warn("OIDC 로그인 실패: 원인={}", exception.getMessage());
    ProblemDetail problemDetail = ErrorCode.AUTH_OAUTH2_LOGIN_FAILED.toProblemDetail();
    errorResponseWriter.write(request, response, problemDetail);
  }

  private AgeRangeConsentRedirector.Result toAgeRangeConsentResult(
      final AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauth2Exception
        && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauth2Exception.getError().getErrorCode())) {
      return AgeRangeConsentRedirector.Result.CANCELLED;
    }
    log.warn("연령대 추가 동의 실패: 원인={}", exception.getMessage());
    return AgeRangeConsentRedirector.Result.FAILED;
  }
}
