package buncheoleasy.global.ratelimit;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Redis 고정 윈도우 카운터 구현. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFixedWindowRateLimiter implements FixedWindowRateLimiter {

  /**
   * INCR 과 만료 설정을 한 번에 처리한다. 둘을 따로 보내면 INCR 직후 만료 설정이 유실됐을 때(커넥션 블립·failover·인스턴스 종료) TTL 없는 키가
   * 남아 해당 주체가 영구히 차단된다.
   */
  private static final RedisScript<Long> INCREMENT_WITH_TTL =
      RedisScript.of(
          """
          local count = redis.call('INCR', KEYS[1])
          if count == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
          end
          return count
          """,
          Long.class);

  private final StringRedisTemplate redisTemplate;

  /** {@inheritDoc} Redis 장애는 로깅 후 통과({@code true})로 처리한다 — 인터페이스의 fail-open 계약. */
  @Override
  public boolean tryAcquire(final String key, final int limit, final Duration window) {
    try {
      Long count =
          redisTemplate.execute(
              INCREMENT_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
      return count == null || count <= limit;
    } catch (DataAccessException exception) {
      log.warn("호출 제한 확인 실패 - 제한 없이 통과시킨다 - key={}", key, exception);
      return true;
    }
  }

  @Override
  public void reset(final String key) {
    try {
      redisTemplate.delete(key);
    } catch (DataAccessException exception) {
      // 리셋 실패는 카운터가 윈도우 만료까지 남는 것뿐이라 호출자에게 전파하지 않는다.
      log.warn("호출 제한 카운터 초기화 실패 - key={}", key, exception);
    }
  }
}
