package buncheoleasy.auth.infrastructure.jwt;

import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.auth.dto.Tokens;
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
    private final RefreshTokenStore refreshTokenStore;

    public JwtTokenProvider(
            @Value("${jwt.secret.access-key}") final String accessSecret,
            @Value("${jwt.secret.refresh-key}") final String refreshSecret,
            @Value("${jwt.access-token-expiration-seconds}") final long accessTokenExpirationSeconds,
            @Value("${jwt.refresh-token-expiration-seconds}") final long refreshTokenExpirationSeconds,
            final RefreshTokenStore refreshTokenStore) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
        this.accessSecretKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshSecretKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshTokenStore = refreshTokenStore;
    }

    public Long parseUserIdFromAccessToken(final String token) {
        final Claims claims = parseClaims(token, accessSecretKey);
        return parseUserId(claims.getSubject());
    }

    public Long parseUserIdFromRefreshToken(final String token) {
        final Claims claims = parseClaims(token, refreshSecretKey);
        return parseUserId(claims.getSubject());
    }

    public Tokens issueTokens(final Long userId) {
        return new Tokens(createAccessToken(userId), createRefreshToken(userId));
    }

    public String createAccessToken(final Long userId) {
        return createToken(userId, accessTokenExpirationSeconds, accessSecretKey);
    }

    public String createRefreshToken(final Long userId) {
        String token = createToken(userId, refreshTokenExpirationSeconds, refreshSecretKey);
        refreshTokenStore.save(userId, token, refreshTokenExpirationSeconds);
        return token;
    }

    public Tokens reissueTokens(final Long userId, final String oldRefreshToken) {
        refreshTokenStore.verify(userId, oldRefreshToken);
        refreshTokenStore.delete(userId);
        return issueTokens(userId);
    }

    private String createToken(final Long userId, final long expirationSeconds, final SecretKey secretKey) {
        final Instant now = Instant.now();
        final Instant expiration = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
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
