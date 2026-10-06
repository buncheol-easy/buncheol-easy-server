package buncheoleasy.auth.infrastructure.oauth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 연령대 추가 동의 결과를 프론트 개최 화면으로 돌려보낸다. 사용자는 이미 로그인한 상태에서 이 흐름을 시작하므로 토큰은 새로 내려주지 않고 결과만 쿼리로
 * 싣는다 — 프론트는 기존 로그인을 유지한 채 개최 자격을 다시 조회한다.
 */
@Component
public class AgeRangeConsentRedirector {

  private static final String RESULT_PARAM = "ageRangeConsent";

  private final String callbackUrl;

  public AgeRangeConsentRedirector(
      @Value("${app.frontend.age-range-consent-callback-url}") final String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public void redirect(final HttpServletResponse response, final Result result)
      throws IOException {
    response.sendRedirect(
        UriComponentsBuilder.fromUriString(callbackUrl)
            .queryParam(RESULT_PARAM, result.value)
            .build()
            .toUriString());
  }

  @RequiredArgsConstructor
  public enum Result {
    AGREED("agreed"),
    CANCELLED("cancelled"),
    FAILED("failed");

    private final String value;
  }
}
