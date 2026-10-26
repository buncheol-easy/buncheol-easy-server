package buncheoleasy.auth.infrastructure.apple;

import buncheoleasy.auth.domain.AppleIdentity;
import buncheoleasy.auth.domain.AppleIdentityVerifier;
import buncheoleasy.auth.domain.Sha256;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * 애플 신원 토큰을 애플 공개키(JWKS)로 검증한다. 공개키는 디코더가 처음 쓸 때 받아 캐시하고(기본 5분), 모르는 키 ID 가 오면 다시 받는다.
 *
 * <p>허용 앱({@code app.apple.signin.audiences}, 번들 ID 쉼표 목록)이 비어 있으면 어떤 토큰도 받지 않는다 — 대상 앱을 확인하지 않으면
 * 다른 앱용으로 발급된 애플 토큰으로도 로그인되기 때문이다.
 */
@Slf4j
@Component
public class AppleIdentityTokenVerifier implements AppleIdentityVerifier {

  private static final String APPLE_ISSUER = "https://appleid.apple.com";
  private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
  private static final String NONCE_CLAIM = "nonce";
  private static final String EMAIL_CLAIM = "email";

  private final NimbusJwtDecoder decoder;
  private final Set<String> audiences;

  @Autowired
  public AppleIdentityTokenVerifier(
      @Value("${app.apple.signin.audiences:}") final String audiences, final Clock clock) {
    this(NimbusJwtDecoder.withJwkSetUri(APPLE_KEYS_URL).build(), parseAudiences(audiences), clock);
  }

  /** 테스트가 로컬 키로 서명한 토큰을 쓰도록 디코더를 바꿔 끼운다. 검증 규칙(발급자·대상 앱·만료)은 여기서 똑같이 건다. */
  AppleIdentityTokenVerifier(
      final NimbusJwtDecoder decoder, final Set<String> audiences, final Clock clock) {
    this.audiences = Set.copyOf(audiences);
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
    // 기본값은 exp 가 없어도 통과시킨다 — 만료 없는 토큰은 영원히 다시 쓸 수 있어 받지 않는다.
    timestampValidator.setAllowEmptyExpiryClaim(false);
    timestampValidator.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestampValidator, new JwtIssuerValidator(APPLE_ISSUER), audienceValidator()));
    this.decoder = decoder;
  }

  @Override
  public AppleIdentity verify(final String identityToken, final String rawNonce) {
    if (audiences.isEmpty()) {
      throw new BusinessException(ErrorCode.AUTH_APPLE_SIGNIN_DISABLED);
    }
    Jwt jwt = decode(identityToken);
    if (!nonceMatches(jwt.getClaimAsString(NONCE_CLAIM), rawNonce)) {
      log.warn("애플 신원 토큰 거절: nonce 불일치");
      throw new BusinessException(ErrorCode.AUTH_APPLE_TOKEN_INVALID);
    }
    return new AppleIdentity(jwt.getSubject(), jwt.getClaimAsString(EMAIL_CLAIM));
  }

  private Jwt decode(final String identityToken) {
    try {
      return decoder.decode(identityToken);
    } catch (BadJwtException exception) {
      // 메시지에는 토큰 값이 실리지 않는다(만료 시각·검증 실패 항목 정도).
      log.warn("애플 신원 토큰 거절: {}", exception.getMessage());
      throw new BusinessException(ErrorCode.AUTH_APPLE_TOKEN_INVALID, exception);
    } catch (JwtException exception) {
      log.error("애플 공개키를 받지 못함", exception);
      throw new BusinessException(ErrorCode.AUTH_APPLE_KEYS_UNAVAILABLE, exception);
    }
  }

  private OAuth2TokenValidator<Jwt> audienceValidator() {
    OAuth2Error error =
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The aud claim is not allowed", null);
    return jwt -> {
      List<String> tokenAudiences = jwt.getAudience();
      boolean allowed =
          tokenAudiences != null && tokenAudiences.stream().anyMatch(audiences::contains);
      return allowed
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(error);
    };
  }

  /** 토큰 nonce 는 앱이 애플에 넘긴 {@code SHA256(rawNonce)} 의 소문자 hex 다. 비교는 상수 시간이다. */
  private static boolean nonceMatches(final String tokenNonce, final String rawNonce) {
    if (tokenNonce == null || rawNonce == null || rawNonce.isEmpty()) {
      return false;
    }
    String expected = HexFormat.of().formatHex(Sha256.hash(rawNonce));
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), tokenNonce.getBytes(StandardCharsets.UTF_8));
  }

  private static Set<String> parseAudiences(final String audiences) {
    return Arrays.stream(audiences.split(","))
        .map(String::trim)
        .filter(audience -> !audience.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}
