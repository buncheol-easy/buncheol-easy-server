package buncheoleasy.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@DisplayName("RedisFixedWindowRateLimiter 테스트")
class RedisFixedWindowRateLimiterTest {

  private static final Duration WINDOW = Duration.ofMinutes(10);

  private StringRedisTemplate redisTemplate;
  private RedisFixedWindowRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    rateLimiter = new RedisFixedWindowRateLimiter(redisTemplate);
  }

  @SuppressWarnings("unchecked")
  private void givenCount(final Long count) {
    given(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString()))
        .willReturn(count);
  }

  @Test
  void 한도_안이면_통과한다() {
    givenCount(3L);

    assertThat(rateLimiter.tryAcquire("K:1", 5, WINDOW)).isTrue();
  }

  @Test
  void 한도와_같은_횟수까지는_통과한다() {
    // 경계: limit 번째 호출은 아직 허용이다 (초과부터 거부).
    givenCount(5L);

    assertThat(rateLimiter.tryAcquire("K:1", 5, WINDOW)).isTrue();
  }

  @Test
  void 한도를_넘으면_거부한다() {
    givenCount(6L);

    assertThat(rateLimiter.tryAcquire("K:1", 5, WINDOW)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void 윈도우_길이를_밀리초로_스크립트에_넘긴다() {
    givenCount(1L);

    rateLimiter.tryAcquire("K:1", 5, WINDOW);

    then(redisTemplate)
        .should()
        .execute(any(RedisScript.class), any(List.class), org.mockito.ArgumentMatchers.eq("600000"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void Redis_장애면_제한_없이_통과시킨다() {
    // fail-open: 제한 장치 장애가 의견 접수 중단·관리자 로그인 전면 차단으로 번지지 않게 한다.
    given(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString()))
        .willThrow(new RedisConnectionFailureException("redis down"));

    assertThat(rateLimiter.tryAcquire("K:1", 5, WINDOW)).isTrue();
  }

  @Test
  void reset_은_키를_지운다() {
    rateLimiter.reset("K:1");

    then(redisTemplate).should().delete("K:1");
  }

  @Test
  void reset_이_Redis_장애로_실패해도_예외를_전파하지_않는다() {
    // 리셋 실패는 카운터가 윈도우 만료까지 남는 것뿐이라 호출자의 정상 경로를 깨지 않는다.
    given(redisTemplate.delete(anyString()))
        .willThrow(new RedisConnectionFailureException("redis down"));

    assertThatCode(() -> rateLimiter.reset("K:1")).doesNotThrowAnyException();
  }
}
