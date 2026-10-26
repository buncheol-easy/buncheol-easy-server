package buncheoleasy.auth.infrastructure.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  private final LoginFailureRedirector loginFailureRedirector;
  private final AgeRangeConsentRedirector ageRangeConsentRedirector;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    if (KakaoAuthorizationRequestResolver.isAgeRangeConsentCallback(request)) {
      ageRangeConsentRedirector.redirect(response, toAgeRangeConsentResult(exception));
      return;
    }
    LoginFailureRedirector.Reason reason =
        isCancelledByUser(exception)
            ? LoginFailureRedirector.Reason.CANCELLED
            : LoginFailureRedirector.Reason.FAILED;
    log.warn("소셜 로그인 실패: 사유={}, 원인코드={}", reason.getValue(), causeCode(exception));
    loginFailureRedirector.redirect(response, reason);
  }

  private AgeRangeConsentRedirector.Result toAgeRangeConsentResult(
      final AuthenticationException exception) {
    if (isCancelledByUser(exception)) {
      return AgeRangeConsentRedirector.Result.CANCELLED;
    }
    log.warn("연령대 추가 동의 실패: 원인={}", exception.getMessage());
    return AgeRangeConsentRedirector.Result.FAILED;
  }

  /** 카카오 동의 화면에서 「취소」를 누르면 카카오는 error=access_denied 로 돌아온다. */
  private static boolean isCancelledByUser(final AuthenticationException exception) {
    return exception instanceof OAuth2AuthenticationException oauth2Exception
        && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauth2Exception.getError().getErrorCode());
  }

  /** 오류 설명 문구는 카카오 응답·콜백 주소에서 온 바깥 문자열이라 로그에는 코드만 남긴다. */
  private static String causeCode(final AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
      return oauth2Exception.getError().getErrorCode();
    }
    return exception.getClass().getSimpleName();
  }
}
