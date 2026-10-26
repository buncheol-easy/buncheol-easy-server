package buncheoleasy.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.push.infrastructure.ApnsTestKeys.MutableClock;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApnsProviderToken 테스트")
class ApnsProviderTokenTest {

  private static final String TEAM_ID = "TEAM000001";
  private static final String KEY_ID = "KEY0000001";
  private static final Instant START = Instant.parse("2026-10-27T00:00:00Z");

  private KeyPair keyPair;
  private MutableClock clock;
  private ApnsProviderToken providerToken;

  @BeforeEach
  void setUp() throws GeneralSecurityException {
    keyPair = ApnsTestKeys.newKeyPair();
    clock = new MutableClock(START);
    providerToken =
        new ApnsProviderToken(
            TEAM_ID,
            KEY_ID,
            ApnsProviderToken.parsePrivateKey(ApnsTestKeys.pem(keyPair.getPrivate())),
            clock);
  }

  private Jws<Claims> verify(final String token) {
    return Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token);
  }

  @Test
  void ES256_으로_서명하고_헤더_kid_클레임_iss_iat_를_싣는다() {
    Jws<Claims> jws = verify(providerToken.current());

    assertThat(jws.getHeader().getAlgorithm()).isEqualTo("ES256");
    assertThat(jws.getHeader().getKeyId()).isEqualTo(KEY_ID);
    assertThat(jws.getPayload().getIssuer()).isEqualTo(TEAM_ID);
    assertThat(jws.getPayload().getIssuedAt().toInstant()).isEqualTo(START);
    // 애플은 iss·iat 외 클레임을 요구하지 않는다 — 만료(exp)를 넣으면 거절 사유가 된다.
    assertThat(jws.getPayload().getExpiration()).isNull();
  }

  @Test
  void 발급_뒤_50분_전까지는_같은_토큰을_다시_쓴다() {
    String first = providerToken.current();

    clock.advance(Duration.ofMinutes(50).minusSeconds(1));

    assertThat(providerToken.current()).isEqualTo(first);
  }

  @Test
  void 발급_뒤_50분이_되면_새로_발급한다() {
    String first = providerToken.current();

    clock.advance(Duration.ofMinutes(50));
    String second = providerToken.current();

    assertThat(second).isNotEqualTo(first);
    assertThat(verify(second).getPayload().getIssuedAt().toInstant())
        .isEqualTo(START.plus(Duration.ofMinutes(50)));
  }

  @Test
  void 거절된_토큰을_버리면_다음엔_새로_발급한다() {
    String first = providerToken.current();
    clock.advance(Duration.ofSeconds(1));

    providerToken.discard(first);

    assertThat(providerToken.current()).isNotEqualTo(first);
  }

  @Test
  void 이미_바뀐_토큰은_예전_토큰으로_버리지_않는다() {
    String first = providerToken.current();
    clock.advance(Duration.ofMinutes(50));
    String second = providerToken.current();

    providerToken.discard(first);

    assertThat(providerToken.current()).isEqualTo(second);
  }

  @Test
  void env_한_줄_값처럼_줄바꿈을_역슬래시_n_글자로_쓴_키도_읽는다() throws Exception {
    String oneLine = ApnsTestKeys.pem(keyPair.getPrivate()).replace("\n", "\\n");

    ApnsProviderToken fromOneLine =
        new ApnsProviderToken(
            TEAM_ID, KEY_ID, ApnsProviderToken.parsePrivateKey(oneLine), clock);

    assertThat(verify(fromOneLine.current()).getPayload().getIssuer()).isEqualTo(TEAM_ID);
  }

  @Test
  void 키가_아닌_값은_GeneralSecurityException_이다() {
    assertThatThrownBy(() -> ApnsProviderToken.parsePrivateKey("not-a-key"))
        .isInstanceOf(GeneralSecurityException.class);
    assertThatThrownBy(() -> ApnsProviderToken.parsePrivateKey("%%%"))
        .isInstanceOf(GeneralSecurityException.class);
  }
}
