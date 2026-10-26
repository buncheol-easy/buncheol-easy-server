package buncheoleasy.auth.infrastructure.oauth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 앱 로그인 결과를 앱 주소({@code app.mobile.login-callback-url}, 기본 {@code buncheoleasy://auth})로 돌려보낸다. 성공이면 일회용
 * 코드만, 실패면 정해진 사유만 싣는다 — 토큰·쿠키는 내려주지 않는다(토큰은 앱이 코드를 바꿀 때 본문으로 받는다).
 */
@Component
public class AppLoginRedirector {

  private static final String CODE_PARAM = "code";
  private static final String SIGNUP_PARAM = "signup";
  private static final String ERROR_PARAM = "error";

  private final String callbackUrl;

  public AppLoginRedirector(@Value("${app.mobile.login-callback-url}") final String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  /** signup=1 은 이번 로그인으로 계정을 새로 만들었다는 표시다(웹 로그인 콜백과 같은 뜻). */
  public void redirectWithCode(
      final HttpServletResponse response, final String code, final boolean newAccount)
      throws IOException {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(callbackUrl).queryParam(CODE_PARAM, code);
    if (newAccount) {
      builder.queryParam(SIGNUP_PARAM, 1);
    }
    response.sendRedirect(builder.build().toUriString());
  }

  public void redirectWithError(
      final HttpServletResponse response, final LoginFailureRedirector.Reason reason)
      throws IOException {
    response.sendRedirect(errorUrl(reason));
  }

  public String errorUrl(final LoginFailureRedirector.Reason reason) {
    return UriComponentsBuilder.fromUriString(callbackUrl)
        .queryParam(ERROR_PARAM, reason.getValue())
        .build()
        .toUriString();
  }
}
