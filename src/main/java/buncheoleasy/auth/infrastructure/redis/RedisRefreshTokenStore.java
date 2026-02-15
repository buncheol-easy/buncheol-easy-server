package buncheoleasy.auth.infrastructure.redis;

import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 Refresh Token 화이트리스트
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "RT:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(final Long userId, final String token, final long expirationTime) {
        redisTemplate.opsForValue().set(
                buildKey(userId),
                token,
                expirationTime,
                TimeUnit.SECONDS
        );
    }

    @Override
    public void delete(final Long userId) {
        redisTemplate.delete(buildKey(userId));
    }

    @Override
    public void verify(final Long userId, final String token) {
        String key = buildKey(userId);
        String savedToken = redisTemplate.opsForValue().get(key);

        if (savedToken == null || savedToken.isBlank() || !savedToken.equals(token)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private String buildKey(final Long userId) {
        return KEY_PREFIX + userId;
    }
}
