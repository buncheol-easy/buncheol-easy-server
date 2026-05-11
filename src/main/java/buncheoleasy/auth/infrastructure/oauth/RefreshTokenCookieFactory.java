package buncheoleasy.auth.infrastructure.oauth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {

  public static final String COOKIE_NAME = "refreshToken";
  private static final String COOKIE_PATH = "/v1/auth";
  private static final String SAME_SITE = "Lax";

  private final long maxAgeSeconds;
  private final boolean secure;

  public RefreshTokenCookieFactory(
      @Value("${jwt.refresh-token-expiration-seconds}") final long maxAgeSeconds,
      @Value("${app.cookie.secure:false}") final boolean secure) {
    this.maxAgeSeconds = maxAgeSeconds;
    this.secure = secure;
  }

  public ResponseCookie create(final String token) {
    return baseBuilder(token).maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
  }

  public ResponseCookie expire() {
    return baseBuilder("").maxAge(0).build();
  }

  private ResponseCookie.ResponseCookieBuilder baseBuilder(final String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(secure)
        .path(COOKIE_PATH)
        .sameSite(SAME_SITE);
  }
}
