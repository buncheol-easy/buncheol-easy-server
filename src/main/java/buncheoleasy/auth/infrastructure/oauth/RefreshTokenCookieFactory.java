package buncheoleasy.auth.infrastructure.oauth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {

  public static final String COOKIE_NAME = "refreshToken";
  private static final String SAME_SITE = "Lax";

  private final long maxAgeSeconds;
  private final boolean secure;
  private final String path;

  public RefreshTokenCookieFactory(
      @Value("${jwt.refresh-token-expiration-seconds}") final long maxAgeSeconds,
      @Value("${app.cookie.secure}") final boolean secure,
      @Value("${app.cookie.refresh-path}") final String path) {
    this.maxAgeSeconds = maxAgeSeconds;
    this.secure = secure;
    this.path = path;
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
        .path(path)
        .sameSite(SAME_SITE);
  }
}
