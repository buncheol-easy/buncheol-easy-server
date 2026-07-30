package buncheoleasy.delivery.infrastructure;

import jakarta.validation.constraints.NotNull;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Delivery Tracker 연동 설정. 시크릿은 환경변수로만 주입하고 비면 연동을 꺼둔다(페이액션과 같은 방식). {@code webhookToken} 은 콜백 발신자
 * 검증용 공유 비밀값 — 콜백에 서명이 없어 콜백 URL 쿼리에 심어둔 이 토큰 비교가 유일한 인증 수단이다.
 */
@Validated
@ConfigurationProperties(prefix = "delivery.tracker")
public record DeliveryTrackerProperties(
    String apiUrl,
    String clientId,
    String clientSecret,
    String callbackUrl,
    String webhookToken,
    @NotNull Duration webhookTtl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {

  /** 추적 조회·웹훅 등록을 호출할 수 있는 환경인지. 미설정 환경에서는 호출을 건너뛴다. */
  public boolean outboundEnabled() {
    return isSet(apiUrl) && isSet(clientId) && isSet(clientSecret);
  }

  /** 수신 콜백을 검증할 수 있는 환경인지. 검증 토큰이 없으면 콜백을 신뢰할 수 없으므로 수신 자체를 거부한다. */
  public boolean webhookEnabled() {
    return isSet(webhookToken) && isSet(callbackUrl);
  }

  /** 웹훅 등록에 쓰는 콜백 URL — 발신자 검증 토큰을 쿼리 파라미터로 심는다. */
  public String tokenizedCallbackUrl() {
    String separator = callbackUrl.contains("?") ? "&" : "?";
    return callbackUrl
        + separator
        + "token="
        + URLEncoder.encode(webhookToken, StandardCharsets.UTF_8);
  }

  private static boolean isSet(final String value) {
    return value != null && !value.isBlank();
  }
}
