package buncheoleasy.auth.infrastructure.oauth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 일반 로그인 실패를 프론트 로그인 콜백으로 돌려보낸다. 사유는 정해진 값만 쿼리로 싣는다 — 카카오가 보낸 error·error_description 을 그대로
 * 실으면 주소 조작으로 아무 글자나 화면에 띄울 수 있다. 토큰·쿠키는 내려주지 않는다.
 */
@Component
public class LoginFailureRedirector {

  private static final String REASON_PARAM = "error";

  private final String callbackUrl;

  public LoginFailureRedirector(
      @Value("${app.frontend.login-callback-url}") final String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public void redirect(final HttpServletResponse response, final Reason reason)
      throws IOException {
    response.sendRedirect(
        UriComponentsBuilder.fromUriString(callbackUrl)
            .queryParam(REASON_PARAM, reason.value)
            .build()
            .toUriString());
  }

  /** 값은 클라 로그인 콜백 화면과 짝이다. */
  @Getter
  @RequiredArgsConstructor
  public enum Reason {
    EMAIL_REQUIRED("email_required"),
    CANCELLED("cancelled"),
    FAILED("failed");

    private final String value;
  }
}
