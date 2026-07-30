package buncheoleasy.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DeliveryTrackerProperties 단위 테스트")
class DeliveryTrackerPropertiesTest {

  private static final Duration TTL = Duration.ofHours(48);
  private static final Duration TIMEOUT = Duration.ofSeconds(3);

  private DeliveryTrackerProperties create(
      final String apiUrl,
      final String clientId,
      final String clientSecret,
      final String callbackUrl,
      final String webhookToken) {
    return new DeliveryTrackerProperties(
        apiUrl, clientId, clientSecret, callbackUrl, webhookToken, TTL, TIMEOUT, TIMEOUT);
  }

  @Nested
  @DisplayName("셀프 비활성 게이트")
  class EnabledGateTest {

    @Test
    void 크리덴셜이_모두_있으면_아웃바운드가_켜진다() {
      DeliveryTrackerProperties properties =
          create("https://api", "id", "secret", "http://cb", "token");

      assertThat(properties.outboundEnabled()).isTrue();
      assertThat(properties.webhookEnabled()).isTrue();
    }

    @Test
    void 크리덴셜이_하나라도_비면_아웃바운드가_꺼진다() {
      assertThat(create("", "id", "secret", "http://cb", "token").outboundEnabled()).isFalse();
      assertThat(create("https://api", "", "secret", "http://cb", "token").outboundEnabled())
          .isFalse();
      assertThat(create("https://api", "id", null, "http://cb", "token").outboundEnabled())
          .isFalse();
    }

    @Test
    void 웹훅_토큰이나_콜백_URL_이_비면_콜백_수신이_꺼진다() {
      assertThat(create("https://api", "id", "secret", "http://cb", "").webhookEnabled())
          .isFalse();
      assertThat(create("https://api", "id", "secret", "", "token").webhookEnabled()).isFalse();
    }
  }

  @Nested
  @DisplayName("tokenizedCallbackUrl — 검증 토큰이 심긴 콜백 URL")
  class TokenizedCallbackUrlTest {

    @Test
    void 토큰을_쿼리_파라미터로_붙인다() {
      DeliveryTrackerProperties properties =
          create("https://api", "id", "secret", "http://cb/callback", "my-token");

      assertThat(properties.tokenizedCallbackUrl()).isEqualTo("http://cb/callback?token=my-token");
    }

    @Test
    void 콜백_URL_에_이미_쿼리가_있으면_앰퍼샌드로_잇는다() {
      DeliveryTrackerProperties properties =
          create("https://api", "id", "secret", "http://cb/callback?env=dev", "my-token");

      assertThat(properties.tokenizedCallbackUrl())
          .isEqualTo("http://cb/callback?env=dev&token=my-token");
    }

    @Test
    void 토큰의_특수문자는_URL_인코딩된다() {
      DeliveryTrackerProperties properties =
          create("https://api", "id", "secret", "http://cb/callback", "a b&c");

      assertThat(properties.tokenizedCallbackUrl())
          .isEqualTo("http://cb/callback?token=a+b%26c");
    }
  }
}
