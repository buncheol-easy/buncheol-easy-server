package buncheoleasy.notification.infrastructure;

import buncheoleasy.notification.domain.SlackChannel;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "slack.webhook")
public record SlackProperties(
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    Map<SlackChannel, String> urls) {

  /** 채널의 웹훅 URL. 미설정 환경(로컬·테스트는 빈 값으로 꺼둔다)이면 null. */
  public String url(final SlackChannel channel) {
    if (urls == null) {
      return null;
    }
    String url = urls.get(channel);
    return (url == null || url.isBlank()) ? null : url;
  }

  /** 채널의 웹훅 URL 이 설정된 환경에서만 발송한다. */
  public boolean enabled(final SlackChannel channel) {
    return url(channel) != null;
  }
}
