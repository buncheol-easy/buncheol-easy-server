package buncheoleasy.auth.infrastructure.redis;

import buncheoleasy.auth.domain.LoginClient;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 Refresh Token 화이트리스트. 웹 칸은 {@code RT:{userId}}(앱 칸이 생기기 전과 같은 키), 앱 칸은 {@code
 * RT:APP:{userId}} 다 — userId 가 숫자라 두 키는 겹치지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

  private static final String WEB_KEY_PREFIX = "RT:";
  private static final String APP_KEY_PREFIX = "RT:APP:";

  private final StringRedisTemplate redisTemplate;

  @Override
  public void save(
      final Long userId, final LoginClient client, final String token, final long expirationTime) {
    redisTemplate
        .opsForValue()
        .set(buildKey(userId, client), token, expirationTime, TimeUnit.SECONDS);
  }

  @Override
  public void delete(final Long userId, final LoginClient client) {
    redisTemplate.delete(buildKey(userId, client));
  }

  @Override
  public void deleteAll(final Long userId) {
    List<String> keys =
        Arrays.stream(LoginClient.values()).map(client -> buildKey(userId, client)).toList();
    redisTemplate.delete(keys);
  }

  @Override
  public void verify(final Long userId, final LoginClient client, final String token) {
    String savedToken = redisTemplate.opsForValue().get(buildKey(userId, client));

    if (savedToken == null || savedToken.isBlank() || !savedToken.equals(token)) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
    }
  }

  private String buildKey(final Long userId, final LoginClient client) {
    return switch (client) {
      case WEB -> WEB_KEY_PREFIX + userId;
      case APP -> APP_KEY_PREFIX + userId;
    };
  }
}
