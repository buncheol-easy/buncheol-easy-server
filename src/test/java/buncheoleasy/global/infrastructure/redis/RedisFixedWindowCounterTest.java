package buncheoleasy.global.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisFixedWindowCounter 단위 테스트")
class RedisFixedWindowCounterTest {

  private static final String KEY = "TEST:counter";

  @Mock private StringRedisTemplate redisTemplate;

  private RedisFixedWindowCounter counter;

  @BeforeEach
  void setUp() {
    counter = new RedisFixedWindowCounter(redisTemplate);
  }

  @Nested
  @DisplayName("increment 테스트")
  class IncrementTest {

    @Test
    @SuppressWarnings("unchecked")
    void 키_하나와_윈도우_밀리초를_스크립트에_넘기고_올린_뒤의_값을_돌려준다() {
      // given — 밀리초가 초로 바뀌면 윈도우가 1000배 짧아져 제한이 사실상 사라진다
      given(redisTemplate.execute(any(RedisScript.class), eq(List.of(KEY)), eq("600000")))
          .willReturn(3L);

      // when
      long count = counter.increment(KEY, Duration.ofMinutes(10));

      // then
      assertThat(count).isEqualTo(3L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void 만료는_윈도우의_첫_증가에서만_한_스크립트로_건다() {
      // INCR 과 PEXPIRE 를 따로 보내면 만료 설정이 유실됐을 때 TTL 없는 키가 남아 영구 차단된다.
      // 매번 만료를 다시 걸면 윈도우가 계속 밀려 도배 중에는 카운터가 영영 초기화되지 않는다.
      given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(1L);

      counter.increment(KEY, Duration.ofMinutes(10));

      ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
      then(redisTemplate).should().execute(script.capture(), anyList(), anyString());
      assertThat(script.getValue().getResultType()).isEqualTo(Long.class);
      assertThat(script.getValue().getScriptAsString())
          .isEqualTo(
              """
              local count = redis.call('INCR', KEYS[1])
              if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
              end
              return count
              """);
    }

    @Test
    @SuppressWarnings("unchecked")
    void Redis_장애는_통과_차단을_정하지_않고_그대로_던진다() {
      // 통과(fail-open)와 차단(fail-closed)은 부르는 쪽이 고른다.
      RedisConnectionFailureException failure = new RedisConnectionFailureException("redis down");
      given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
          .willThrow(failure);

      assertThatThrownBy(() -> counter.increment(KEY, Duration.ofMinutes(10))).isSameAs(failure);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 값을_받지_못하면_장애와_같은_예외로_던진다() {
      // 0 으로 바꿔 돌려주면 차단을 고른 쪽이 조용히 통과된다.
      given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(null);

      assertThatThrownBy(() -> counter.increment(KEY, Duration.ofMinutes(10)))
          .isInstanceOf(DataAccessException.class);
    }
  }

  @Nested
  @DisplayName("reset 테스트")
  class ResetTest {

    @Test
    void 키를_지운다() {
      counter.reset(KEY);

      then(redisTemplate).should().delete(KEY);
    }

    @Test
    void Redis_장애는_그대로_던진다() {
      RedisConnectionFailureException failure = new RedisConnectionFailureException("redis down");
      given(redisTemplate.delete(KEY)).willThrow(failure);

      assertThatThrownBy(() -> counter.reset(KEY)).isSameAs(failure);
    }
  }
}
