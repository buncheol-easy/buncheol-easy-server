package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 앱 로그인 결과를 앱 주소({@code app.mobile.login-callback-url})로 돌려보낸다. 성공이면 일회용 코드만, 실패면 정해진 사유만 싣는다 —
 * 토큰·쿠키는 내려주지 않는다(토큰은 앱이 코드를 바꿀 때 본문으로 받는다).
 *
 * <p>주소가 비어 있으면 앱 카카오 로그인 전체가 꺼진다. 커스텀 스킴은 iOS 가 주인을 확인하지 않아, 같은 스킴을 등록한 다른 앱이 시작 요청을 직접 열면
 * 코드가 그 앱으로 간다(RFC 8252 §8.6 클라이언트 사칭) — 켜는 것은 그 위험을 받아들이는 운영 결정이라 기본값을 두지 않는다. 값이 있으면 기동 때
 * 스킴 있는 절대 주소인지 확인한다(빈 값·상대 주소로 {@code ?code=…} 가 이 서버 경로로 새지 않게).
 */
@Component
public class AppLoginRedirector {

  private static final String CODE_PARAM = "code";
  private static final String SIGNUP_PARAM = "signup";
  private static final String ERROR_PARAM = "error";

  // 꺼져 있으면 null
  private final String callbackUrl;

  public AppLoginRedirector(
      @Value("${app.mobile.login-callback-url:}") final String callbackUrl) {
    this.callbackUrl = validate(callbackUrl);
  }

  public boolean isEnabled() {
    return callbackUrl != null;
  }

  /** signup=1 은 이번 로그인으로 계정을 새로 만들었다는 표시다(웹 로그인 콜백과 같은 뜻). */
  public void redirectWithCode(
      final HttpServletResponse response, final String code, final boolean newAccount)
      throws IOException {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(requireCallbackUrl()).queryParam(CODE_PARAM, code);
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
    return UriComponentsBuilder.fromUriString(requireCallbackUrl())
        .queryParam(ERROR_PARAM, reason.getValue())
        .build()
        .toUriString();
  }

  // 호출부가 isEnabled() 를 먼저 본다. 여기는 그 확인이 빠졌을 때 코드가 상대 주소로 새지 않게 하는 마지막 막이다.
  private String requireCallbackUrl() {
    if (callbackUrl == null) {
      throw new BusinessException(ErrorCode.AUTH_APP_LOGIN_DISABLED);
    }
    return callbackUrl;
  }

  private static String validate(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.strip();
    URI uri;
    try {
      uri = new URI(trimmed);
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException(invalidMessage(trimmed), exception);
    }
    // 스킴·호스트 자리(//)가 있어야 하고, # 뒤에 붙으면 앱이 코드를 쿼리로 읽지 못한다.
    if (uri.getScheme() == null || uri.isOpaque() || uri.getRawFragment() != null) {
      throw new IllegalArgumentException(invalidMessage(trimmed));
    }
    return trimmed;
  }

  private static String invalidMessage(final String value) {
    return "APP_LOGIN_CALLBACK_URL(app.mobile.login-callback-url) 은 스킴 있는 절대 주소여야 한다"
        + "(예: buncheoleasy://auth): "
        + value;
  }
}
