package buncheoleasy.auth.infrastructure.jwt;

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

@Component
public class JwtTokenProvider {

    private final long accessTokenExpirationSeconds;
    private final long refreshTokenExpirationSeconds;
    private final SecretKey accessSecretKey;
    private final SecretKey refreshSecretKey;

    public JwtTokenProvider(
            @Value("${jwt.secret.access-key}") final String accessSecret,
            @Value("${jwt.secret.refresh-key}") final String refreshSecret,
            @Value("${jwt.access-token-expiration-seconds}") final long accessTokenExpirationSeconds,
            @Value("${jwt.refresh-token-expiration-seconds}") final long refreshTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
        this.accessSecretKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshSecretKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    public Long parseUserIdFromAccessToken(final String token) {
        final Claims claims = parseClaims(token, accessSecretKey);
        return parseUserId(claims.getSubject());
    }

    public Long parseUserIdFromRefreshToken(final String token) {
        final Claims claims = parseClaims(token, refreshSecretKey);
        return parseUserId(claims.getSubject());
    }

    public String createAccessToken(final Long userId) {
        final Instant now = Instant.now();
        final Instant expiration = now.plusSeconds(accessTokenExpirationSeconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(accessSecretKey)
                .compact();
    }

    public String createRefreshToken(final Long userId) {
        final Instant now = Instant.now();
        final Instant expiration = now.plusSeconds(refreshTokenExpirationSeconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(refreshSecretKey)
                .compact();
    }

    private Claims parseClaims(final String token, final SecretKey secretKey) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
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
