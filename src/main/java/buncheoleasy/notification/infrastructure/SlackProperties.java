package buncheoleasy.notification.infrastructure;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "slack.webhook")
public record SlackProperties(
    String url, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {

  /** 웹훅 URL 이 설정된 환경에서만 발송한다. 로컬·테스트는 빈 값으로 꺼둔다. */
  public boolean enabled() {
    return url != null && !url.isBlank();
  }
}
