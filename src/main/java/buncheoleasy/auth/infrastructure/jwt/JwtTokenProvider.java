package buncheoleasy.auth.infrastructure.jwt;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 발급/파싱. 토큰은 두 종류다 — 유저 토큰(role claim 없음, {@code ROLE_USER})과 관리자 토큰(role claim = "ADMIN",
 * {@code ROLE_ADMIN}). role claim 은 권한 등급이 아니라 토큰 종류 구분자다. 서비스 유저와 관리자 계정(admins)은 id 공간이 분리돼 있어
 * subject 가 겹칠 수 있으므로, 인가는 반드시 role 로 구분한다 (유저 API 는 hasRole(USER) — 관리자 토큰으로 같은 id 의 유저 위장 불가).
 *
 * <p>관리자 토큰은 refresh 없이 access 단독이다 — refresh 저장소(Redis)가 userId 키라 admin id 와 충돌하고, 관리자 1~2명 규모에서
 * 만료 시 재로그인이 회전 관리보다 단순하기 때문. 대신 수명을 별도 설정(기본 12시간)으로 늘린다.
 */
@Component
public class JwtTokenProvider {

  private static final String ROLE_CLAIM = "role";
  private static final String ADMIN_ROLE = "ADMIN";
  private static final String IMPERSONATION_CLAIM = "imp";

  private final long accessTokenExpirationSeconds;
  private final long refreshTokenExpirationSeconds;
  private final long adminTokenExpirationSeconds;
  private final long impersonationTokenExpirationSeconds;
  private final SecretKey accessSecretKey;
  private final SecretKey refreshSecretKey;
  private final RefreshTokenStore refreshTokenStore;

  public JwtTokenProvider(
      @Value("${jwt.secret.access-key}") final String accessSecret,
      @Value("${jwt.secret.refresh-key}") final String refreshSecret,
      @Value("${jwt.access-token-expiration-seconds}") final long accessTokenExpirationSeconds,
      @Value("${jwt.refresh-token-expiration-seconds}") final long refreshTokenExpirationSeconds,
      @Value("${jwt.admin-token-expiration-seconds:43200}") final long adminTokenExpirationSeconds,
      @Value("${jwt.impersonation-token-expiration-seconds:900}")
          final long impersonationTokenExpirationSeconds,
      final RefreshTokenStore refreshTokenStore) {
    this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    this.adminTokenExpirationSeconds = adminTokenExpirationSeconds;
    this.impersonationTokenExpirationSeconds = impersonationTokenExpirationSeconds;
    this.accessSecretKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
    this.refreshSecretKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    this.refreshTokenStore = refreshTokenStore;
  }

  public Long parseUserIdFromAccessToken(final String token) {
    return parseAccessTokenClaims(token).userId();
  }

  /** access token 에서 subject(유저 또는 관리자 id)와 role claim(유저 토큰이면 null)을 함께 파싱한다. */
  public AccessTokenClaims parseAccessTokenClaims(final String token) {
    final Claims claims = parseClaims(token, accessSecretKey);
    return new AccessTokenClaims(
        parseUserId(claims.getSubject()),
        claims.get(ROLE_CLAIM, String.class),
        Boolean.TRUE.equals(claims.get(IMPERSONATION_CLAIM, Boolean.class)));
  }

  public Long parseUserIdFromRefreshToken(final String token) {
    final Claims claims = parseClaims(token, refreshSecretKey);
    return parseUserId(claims.getSubject());
  }

  /** 유저 access/refresh 토큰 쌍 발급. */
  public TokenPair issueTokens(final Long userId) {
    return new TokenPair(createAccessToken(userId), createRefreshToken(userId));
  }

  public String createAccessToken(final Long userId) {
    return buildAccessToken(userId, null, false, accessTokenExpirationSeconds);
  }

  /** 관리자 access token 발급 (ID/PW 로그인). role claim 으로 유저 토큰과 구분되며 refresh 는 없다. */
  public String createAdminAccessToken(final Long adminId) {
    return buildAccessToken(adminId, ADMIN_ROLE, false, adminTokenExpirationSeconds);
  }

  /**
   * 관리자가 문의 재현용으로 발급하는 대상 유저의 access token. 일반 유저 토큰과 동일하게 role claim 이 없어({@code
   * ROLE_USER}) 유저 API 를 그대로 호출할 수 있고, 대신 수명이 매우 짧다(기본 15분). refresh 는 발급하지 않으므로 만료되면 관리자가 다시
   * 발급한다 — 유저 본인 세션(refresh 저장소)은 전혀 건드리지 않아 강제 로그아웃되지 않는다.
   */
  public String createImpersonationAccessToken(final Long userId) {
    return buildAccessToken(userId, null, true, impersonationTokenExpirationSeconds);
  }

  public long getImpersonationTokenExpirationSeconds() {
    return impersonationTokenExpirationSeconds;
  }

  public String createRefreshToken(final Long userId) {
    final Instant now = Instant.now();
    final Instant expiration = now.plusSeconds(refreshTokenExpirationSeconds);

    String token =
        Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(refreshSecretKey)
            .compact();

    refreshTokenStore.save(userId, token, refreshTokenExpirationSeconds);
    return token;
  }

  public TokenPair reissueTokens(final Long userId, final String oldRefreshToken) {
    refreshTokenStore.verify(userId, oldRefreshToken);
    refreshTokenStore.delete(userId);
    return issueTokens(userId);
  }

  private String buildAccessToken(
      final Long subjectId,
      final String role,
      final boolean impersonated,
      final long expirationSeconds) {
    final Instant now = Instant.now();
    final Instant expiration = now.plusSeconds(expirationSeconds);

    final var builder =
        Jwts.builder()
            .subject(String.valueOf(subjectId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(accessSecretKey);
    if (role != null) {
      builder.claim(ROLE_CLAIM, role);
    }
    if (impersonated) {
      builder.claim(IMPERSONATION_CLAIM, true);
    }
    return builder.compact();
  }

  private Claims parseClaims(final String token, final SecretKey secretKey) {
    try {
      return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    } catch (final ExpiredJwtException exception) {
      throw new BusinessException(ErrorCode.AUTH_EXPIRED_TOKEN, exception);
    } catch (final JwtException | IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, exception);
    }
  }

  private Long parseUserId(final String subject) {
    try {
      return Long.parseLong(subject);
    } catch (final NumberFormatException exception) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, exception);
    }
  }
}
