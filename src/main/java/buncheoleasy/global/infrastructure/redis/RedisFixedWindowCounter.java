package buncheoleasy.global.infrastructure.redis;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 윈도우 카운터. 키 하나를 첫 증가 시점부터 {@code window} 동안 세고, 윈도우가 지나면 키가 사라져 0 부터 다시 센다.
 *
 * <p>Redis 장애는 {@link DataAccessException} 으로 그대로 던진다. 통과(fail-open)와 차단(fail-closed)은 쓰임새마다 답이
 * 달라서 — 의견 보내기·관리자 로그인은 통과, 비용이 나가는 호출은 차단 — 부르는 쪽이 잡아서 고른다.
 */
@Component
@RequiredArgsConstructor
public class RedisFixedWindowCounter {

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

  /**
   * 카운터를 1 올리고 올린 뒤의 값을 돌려준다. 만료는 윈도우의 첫 증가에서만 걸어, 이후 증가가 윈도우를 늘리지 않는다.
   *
   * @throws DataAccessException Redis 에 닿지 못했거나 값을 받지 못했을 때
   */
  public long increment(final String key, final Duration window) {
    Long count =
        redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
    if (count == null) {
      // 파이프라인·MULTI 안에서만 생긴다. 0 으로 바꾸면 차단을 고른 쪽이 조용히 통과되므로 장애와 같은 길로 보낸다.
      throw new DataRetrievalFailureException("Redis 카운터 증가 결과를 받지 못했다");
    }
    return count;
  }

  /**
   * 카운터를 지워 윈도우를 처음부터 다시 시작하게 한다.
   *
   * @throws DataAccessException Redis 에 닿지 못했을 때
   */
  public void reset(final String key) {
    redisTemplate.delete(key);
  }
}
