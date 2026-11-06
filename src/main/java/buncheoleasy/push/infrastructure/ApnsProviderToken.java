package buncheoleasy.push.infrastructure;

import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * APNs 제공자 토큰(ES256 JWT — 헤더 {@code kid}, 클레임 {@code iss}·{@code iat}). 발급한 토큰을 50분 동안 다시 쓴다 — 애플은 발급 1시간이 지난
 * 토큰을 거절하고(ExpiredProviderToken), 20분보다 자주 새로 만들면 막는다(TooManyProviderTokenUpdates). 발송 스레드가 여럿이라 동기화한다.
 */
final class ApnsProviderToken {

  static final Duration REUSE_FOR = Duration.ofMinutes(50);

  private final String teamId;
  private final String keyId;
  private final PrivateKey signingKey;
  private final Clock clock;

  private String token;
  private Instant issuedAt;

  ApnsProviderToken(
      final String teamId, final String keyId, final PrivateKey signingKey, final Clock clock) {
    this.teamId = teamId;
    this.keyId = keyId;
    this.signingKey = signingKey;
    this.clock = clock;
  }

  synchronized String current() {
    Instant now = clock.instant();
    if (token == null || !now.isBefore(issuedAt.plus(REUSE_FOR))) {
      token = issue(now);
      issuedAt = now;
    }
    return token;
  }

  /** 애플이 만료로 거절한 토큰을 버린다. 그 사이 다른 스레드가 새로 만든 토큰은 건드리지 않는다. */
  synchronized void discard(final String rejected) {
    if (rejected.equals(token)) {
      token = null;
    }
  }

  private String issue(final Instant now) {
    return Jwts.builder()
        .header()
        .keyId(keyId)
        .and()
        .issuer(teamId)
        .issuedAt(Date.from(now))
        .signWith(signingKey, Jwts.SIG.ES256)
        .compact();
  }

  /** .p8 본문(PEM, PKCS#8 EC)을 읽는다. env 한 줄 값의 {@code \n} 글자도 줄바꿈으로 본다. */
  static PrivateKey parsePrivateKey(final String pem) throws GeneralSecurityException {
    String base64 =
        pem.replace("\\n", "\n")
            .replaceAll("-----(BEGIN|END) [A-Z ]*PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    final byte[] der;
    try {
      der = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException e) {
      throw new GeneralSecurityException("APNs 키가 base64 가 아니다", e);
    }
    return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
  }
}
