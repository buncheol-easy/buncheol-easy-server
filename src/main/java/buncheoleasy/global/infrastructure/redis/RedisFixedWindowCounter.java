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

  /**
   * 키가 살아 있고 0 보다 클 때만 1 내린다. 만료된 키에 그냥 DECR 하면 TTL 없는 -1 키가 생겨, 그 뒤 INCR 이 0 에서 시작해 첫 증가로 보지
   * 못하고 만료도 걸지 않는다 — 영구 키가 된다.
   */
  private static final RedisScript<Long> DECREMENT_IF_PRESENT =
      RedisScript.of(
          """
          local current = redis.call('GET', KEYS[1])
          if current and tonumber(current) > 0 then
            return redis.call('DECR', KEYS[1])
          end
          return -1
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
   * 카운터를 1 내린다(쓴 횟수 돌려주기). 키가 없거나(만료) 이미 0 이면 아무것도 하지 않는다. DECR 은 남은 만료 시간을 그대로 둔다.
   *
   * @throws DataAccessException Redis 에 닿지 못했을 때
   */
  public void decrement(final String key) {
    redisTemplate.execute(DECREMENT_IF_PRESENT, List.of(key));
  }

  /**
   * 지금 값을 읽는다. 키가 없으면(윈도우가 아직 안 열렸거나 만료) 0 이다.
   *
   * @throws DataAccessException Redis 에 닿지 못했을 때
   */
  public long count(final String key) {
    String value = redisTemplate.opsForValue().get(key);
    return value == null ? 0L : Long.parseLong(value);
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
