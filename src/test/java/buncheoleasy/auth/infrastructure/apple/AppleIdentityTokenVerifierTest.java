package buncheoleasy.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.auth.domain.AppleIdentity;
import buncheoleasy.auth.domain.Sha256;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 애플 공개키 대신 테스트에서 만든 RSA 키로 서명·검증한다 — 네트워크를 쓰지 않는다. */
@DisplayName("AppleIdentityTokenVerifier 단위 테스트")
class AppleIdentityTokenVerifierTest {

  private static final String BUNDLE_ID = "com.buncheoleasy.app";
  private static final String RAW_NONCE = "raw-nonce-0123456789";
  private static final String SUBJECT = "001234.0a1b2c3d4e5f60718293a4b5c6d7e8f9.0123";
  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private static KeyPair appleKey;
  private static KeyPair otherKey;

  @BeforeAll
  static void createKeys() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    appleKey = generator.generateKeyPair();
    otherKey = generator.generateKeyPair();
  }

  private static AppleIdentityTokenVerifier verifier(final Set<String> audiences) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey((RSAPublicKey) appleKey.getPublic()).build();
    return new AppleIdentityTokenVerifier(decoder, audiences, CLOCK);
  }

  private static String hashedNonce(final String rawNonce) {
    return HexFormat.of().formatHex(Sha256.hash(rawNonce));
  }

  /** 애플이 발급하는 모양의 클레임. customizer 로 한 항목씩 틀리게 만든다. */
  private static JWTClaimsSet claims(final Consumer<JWTClaimsSet.Builder> customizer) {
    JWTClaimsSet.Builder builder =
        new JWTClaimsSet.Builder()
            .issuer("https://appleid.apple.com")
            .audience(BUNDLE_ID)
            .subject(SUBJECT)
            .issueTime(Date.from(NOW.minusSeconds(10)))
            .expirationTime(Date.from(NOW.plusSeconds(600)))
            .claim("nonce", hashedNonce(RAW_NONCE))
            .claim("email", "relay@privaterelay.appleid.com");
    customizer.accept(builder);
    return builder.build();
  }

  private static String sign(final JWTClaimsSet claims, final KeyPair key) throws JOSEException {
    SignedJWT jwt =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(), claims);
    jwt.sign(new RSASSASigner(key.getPrivate()));
    return jwt.serialize();
  }

  private static void assertRejected(final Runnable call, final ErrorCode errorCode) {
    assertThatThrownBy(call::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Nested
  @DisplayName("받아들이는 토큰")
  class AcceptTest {

    @Test
    void 애플_서명_발급자_대상앱_만료_nonce_가_맞으면_sub_와_email_을_돌려준다() throws Exception {
      // given
      String token = sign(claims(builder -> {}), appleKey);

      // when
      AppleIdentity identity = verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE);

      // then
      assertThat(identity.subject()).isEqualTo(SUBJECT);
      assertThat(identity.email()).isEqualTo("relay@privaterelay.appleid.com");
    }

    @Test
    void 허용_앱이_여럿이면_그중_하나와_맞으면_된다() throws Exception {
      // given
      String token = sign(claims(builder -> builder.audience(List.of("com.other", BUNDLE_ID))), appleKey);

      // when & then
      assertThat(verifier(Set.of("com.buncheoleasy.app.dev", BUNDLE_ID)).verify(token, RAW_NONCE))
          .isNotNull();
    }

    @Test
    void email_이_없으면_null_로_돌려준다() throws Exception {
      // given
      String token = sign(claims(builder -> builder.claim("email", null)), appleKey);

      // when & then
      assertThat(verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE).email()).isNull();
    }
  }

  @Nested
  @DisplayName("거절하는 토큰")
  class RejectTest {

    @Test
    void 다른_키로_서명한_위조_토큰() throws Exception {
      String token = sign(claims(builder -> {}), otherKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 서명_없는_토큰() {
      String token = new PlainJWT(claims(builder -> {})).serialize();

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 대칭키_HS256_으로_서명한_토큰() throws Exception {
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(builder -> {}));
      jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef0123456789abcdef"));
      String token = jwt.serialize();

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 대상_앱이_다른_토큰() throws Exception {
      String token = sign(claims(builder -> builder.audience("com.someone.else")), appleKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 대상_앱이_없는_토큰() throws Exception {
      String token = sign(claims(builder -> builder.audience((String) null)), appleKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 발급자가_애플이_아닌_토큰() throws Exception {
      String token = sign(claims(builder -> builder.issuer("https://evil.example.com")), appleKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 만료된_토큰() throws Exception {
      // 기본 허용 오차 60초보다 더 지난 토큰
      String token =
          sign(claims(builder -> builder.expirationTime(Date.from(NOW.minusSeconds(120)))), appleKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 만료_시각이_없는_토큰() throws Exception {
      String token = sign(claims(builder -> builder.expirationTime(null)), appleKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void nonce_가_다른_토큰() throws Exception {
      String token = sign(claims(builder -> {}), appleKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, "another-raw-nonce"),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 해시하지_않은_rawNonce_가_그대로_실린_토큰() throws Exception {
      String token = sign(claims(builder -> builder.claim("nonce", RAW_NONCE)), appleKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void nonce_가_없는_토큰() throws Exception {
      String token = sign(claims(builder -> builder.claim("nonce", null)), appleKey);

      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify(token, RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }

    @Test
    void 토큰_모양이_아닌_값() {
      assertRejected(
          () -> verifier(Set.of(BUNDLE_ID)).verify("not-a-jwt", RAW_NONCE),
          ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }
  }

  @Nested
  @DisplayName("설정·외부 상태")
  class ConfigurationTest {

    @Test
    void 허용_앱이_설정되지_않으면_올바른_토큰도_거절한다() throws Exception {
      String token = sign(claims(builder -> {}), appleKey);

      assertRejected(
          () -> verifier(Set.of()).verify(token, RAW_NONCE), ErrorCode.AUTH_APPLE_SIGNIN_DISABLED);
    }

    @Test
    void 운영_생성자는_쉼표_목록의_공백과_빈_항목을_버린다() throws Exception {
      // 운영 생성자는 애플 공개키 주소를 쓰지만, 허용 앱이 비면 디코더를 부르기 전에 끝난다(네트워크 없음).
      assertRejected(
          () -> new AppleIdentityTokenVerifier(" , ,", CLOCK).verify("any", RAW_NONCE),
          ErrorCode.AUTH_APPLE_SIGNIN_DISABLED);
      assertRejected(
          () -> new AppleIdentityTokenVerifier("", CLOCK).verify("any", RAW_NONCE),
          ErrorCode.AUTH_APPLE_SIGNIN_DISABLED);
    }

    @Test
    void 공개키를_받아_오지_못하면_잠시_뒤_다시_하라고_알린다() throws Exception {
      // 닫힌 로컬 포트 — 외부 네트워크를 쓰지 않고 공개키 조회 실패를 만든다.
      NimbusJwtDecoder unreachable =
          NimbusJwtDecoder.withJwkSetUri("http://127.0.0.1:9/auth/keys").build();
      AppleIdentityTokenVerifier verifier =
          new AppleIdentityTokenVerifier(unreachable, Set.of(BUNDLE_ID), CLOCK);
      String token = sign(claims(builder -> {}), appleKey);

      assertRejected(
          () -> verifier.verify(token, RAW_NONCE), ErrorCode.AUTH_APPLE_KEYS_UNAVAILABLE);
    }
  }
}
