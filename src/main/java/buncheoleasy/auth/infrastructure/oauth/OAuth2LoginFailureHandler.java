package buncheoleasy.auth.infrastructure.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
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

  private static final Pattern ERROR_CODE_SHAPE = Pattern.compile("[a-z_]{1,64}");
  private static final String MALFORMED_ERROR_CODE = "malformed";

  private final LoginFailureRedirector loginFailureRedirector;
  private final AgeRangeConsentRedirector ageRangeConsentRedirector;
  private final AppLoginRedirector appLoginRedirector;

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
    // 앱 인증 창에서 시작한 로그인은 웹 화면이 아니라 앱 주소로 돌려보내야 인증 창이 닫힌다(앱 로그인이 꺼져 있으면 웹 화면으로).
    if (KakaoAuthorizationRequestResolver.isAppLoginCallback(request)
        && appLoginRedirector.isEnabled()) {
      appLoginRedirector.redirectWithError(response, reason);
      return;
    }
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

  /**
   * 로그에는 오류 설명 문구를 빼고 코드만 남긴다. 코드도 저장된 인가 요청이 있으면 콜백 주소의 error 값 그대로라, 코드 모양(소문자·밑줄)일 때만
   * 남기고 아니면 고정값으로 바꾼다 — 줄바꿈을 넣어 가짜 로그 줄을 끼우지 못하게.
   */
  private static String causeCode(final AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
      String errorCode = oauth2Exception.getError().getErrorCode();
      return ERROR_CODE_SHAPE.matcher(errorCode).matches() ? errorCode : MALFORMED_ERROR_CODE;
    }
    return exception.getClass().getSimpleName();
  }
}
