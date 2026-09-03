package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

@DisplayName("RefreshTokenCookieFactory 단위 테스트")
class RefreshTokenCookieFactoryTest {

  private static final long MAX_AGE_SECONDS = 1209600;
  private static final String PATH = "/api/backend/v1/auth";

  private final RefreshTokenCookieFactory factory =
      new RefreshTokenCookieFactory(MAX_AGE_SECONDS, true, PATH);

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("설정된 Path 와 보안 속성으로 쿠키를 만든다")
    void 설정된_Path_와_보안_속성으로_쿠키를_만든다() {
      ResponseCookie cookie = factory.create("refresh-token-value");

      assertThat(cookie.getName()).isEqualTo(RefreshTokenCookieFactory.COOKIE_NAME);
      assertThat(cookie.getValue()).isEqualTo("refresh-token-value");
      assertThat(cookie.getPath()).isEqualTo(PATH);
      assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(MAX_AGE_SECONDS));
      assertThat(cookie.isHttpOnly()).isTrue();
      assertThat(cookie.isSecure()).isTrue();
      assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }
  }

  @Nested
  @DisplayName("expire")
  class Expire {

    // 삭제 쿠키의 Path 가 발급 때와 다르면 브라우저가 다른 쿠키로 보고 원본을 안 지운다.
    @Test
    @DisplayName("발급 때와 같은 Path 로 만료 쿠키를 만든다")
    void 발급_때와_같은_Path_로_만료_쿠키를_만든다() {
      ResponseCookie cookie = factory.expire();

      assertThat(cookie.getPath()).isEqualTo(factory.create("any").getPath());
      assertThat(cookie.getValue()).isEmpty();
      assertThat(cookie.getMaxAge()).isZero();
    }
  }
}
