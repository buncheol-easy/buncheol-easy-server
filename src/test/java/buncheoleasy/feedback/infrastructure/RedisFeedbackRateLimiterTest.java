package buncheoleasy.feedback.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.ratelimit.FixedWindowRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 카운팅 자체(Redis 스크립트·TTL·fail-open)는 {@code RedisFixedWindowRateLimiterTest} 가 검증한다. 여기서는 이 클래스가 가진
 * <b>정책</b> — 두 겹 제한과 거부 시 에러 — 만 본다.
 */
@DisplayName("RedisFeedbackRateLimiter 테스트")
class RedisFeedbackRateLimiterTest {

  private static final String CLIENT_KEY = "ip:1.2.3.4";
  private static final String GLOBAL_REDIS_KEY = "FB:global";
  private static final String CLIENT_REDIS_KEY = "FB:" + CLIENT_KEY;

  private FixedWindowRateLimiter delegate;
  private RedisFeedbackRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    delegate = mock(FixedWindowRateLimiter.class);
    rateLimiter =
        new RedisFeedbackRateLimiter(
            delegate, new FeedbackRateLimitProperties(5, 60, Duration.ofMinutes(10)));
  }

  private void givenWithinLimit(final boolean global, final boolean client) {
    given(delegate.tryAcquire(eq(GLOBAL_REDIS_KEY), anyInt(), any())).willReturn(global);
    given(delegate.tryAcquire(eq(CLIENT_REDIS_KEY), anyInt(), any())).willReturn(client);
  }

  @Test
  void 한도_안이면_통과한다() {
    givenWithinLimit(true, true);

    assertThatCode(() -> rateLimiter.checkAndRecord(CLIENT_KEY)).doesNotThrowAnyException();
  }

  @Test
  void 주체별_한도를_넘으면_거부한다() {
    givenWithinLimit(true, false);

    assertThatThrownBy(() -> rateLimiter.checkAndRecord(CLIENT_KEY))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FEEDBACK_RATE_LIMITED);
  }

  @Test
  void 전역_한도를_넘으면_주체_한도와_무관하게_거부한다() {
    // 헤더 조작으로 주체 키를 매번 바꿔도 슬랙에 흘러드는 총량은 전역 상한이 묶는다.
    given(delegate.tryAcquire(eq(GLOBAL_REDIS_KEY), anyInt(), any())).willReturn(false);

    assertThatThrownBy(() -> rateLimiter.checkAndRecord("ip:매번-다른-키"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FEEDBACK_RATE_LIMITED);
  }

  @Test
  void 각_한도를_설정값_그대로_위임한다() {
    givenWithinLimit(true, true);

    rateLimiter.checkAndRecord(CLIENT_KEY);

    then(delegate).should().tryAcquire(GLOBAL_REDIS_KEY, 60, Duration.ofMinutes(10));
    then(delegate).should().tryAcquire(CLIENT_REDIS_KEY, 5, Duration.ofMinutes(10));
  }
}
