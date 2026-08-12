package buncheoleasy.feedback.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import buncheoleasy.global.exception.domain.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@DisplayName("RedisFeedbackRateLimiter 테스트")
class RedisFeedbackRateLimiterTest {

  private StringRedisTemplate redisTemplate;
  private RedisFeedbackRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    rateLimiter =
        new RedisFeedbackRateLimiter(
            redisTemplate, new FeedbackRateLimitProperties(5, 60, Duration.ofMinutes(10)));
  }

  @SuppressWarnings("unchecked")
  private void givenCounts(final Long globalCount, final Long clientCount) {
    given(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString()))
        .willReturn(globalCount, clientCount);
  }

  @Test
  void 한도_안이면_통과한다() {
    givenCounts(1L, 1L);

    assertThatCode(() -> rateLimiter.checkAndRecord("ip:1.2.3.4")).doesNotThrowAnyException();
  }

  @Test
  void 주체별_한도를_넘으면_거부한다() {
    givenCounts(10L, 6L);

    assertThatThrownBy(() -> rateLimiter.checkAndRecord("ip:1.2.3.4"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void 전역_한도를_넘으면_주체_한도와_무관하게_거부한다() {
    // 헤더 조작으로 주체 키를 매번 바꿔도 슬랙에 흘러드는 총량은 전역 상한이 묶는다.
    givenCounts(61L, 1L);

    assertThatThrownBy(() -> rateLimiter.checkAndRecord("ip:매번-다른-키"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void Redis_장애면_제한_없이_통과시킨다() {
    // fail-open: 제한 장치가 죽었다고 의견 접수까지 막지는 않는다.
    given(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString()))
        .willThrow(new RedisConnectionFailureException("redis down"));

    assertThatCode(() -> rateLimiter.checkAndRecord("ip:1.2.3.4")).doesNotThrowAnyException();
  }
}
