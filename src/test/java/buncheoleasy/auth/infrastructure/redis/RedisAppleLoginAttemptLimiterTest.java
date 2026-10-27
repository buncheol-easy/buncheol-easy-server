package buncheoleasy.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.infrastructure.redis.RedisFixedWindowCounter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisAppleLoginAttemptLimiter 단위 테스트")
class RedisAppleLoginAttemptLimiterTest {

  private static final String KEY = "APPLE_LOGIN:fail:ip:203.0.113.9";

  @Mock private RedisFixedWindowCounter counter;

  private RedisAppleLoginAttemptLimiter limiter;

  @BeforeEach
  void setUp() {
    limiter = new RedisAppleLoginAttemptLimiter(counter, 10, Duration.ofMinutes(10));
  }

  @Test
  void 실패가_한도_아래면_통과한다() {
    given(counter.count(KEY)).willReturn(9L);

    assertThatCode(() -> limiter.checkAllowed("203.0.113.9")).doesNotThrowAnyException();
  }

  @Test
  void 실패가_한도에_닿으면_429_로_거절한다() {
    given(counter.count(KEY)).willReturn(10L);

    assertThatThrownBy(() -> limiter.checkAllowed("203.0.113.9"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTH_APPLE_LOGIN_RATE_LIMITED);
  }

  @Test
  void 실패는_윈도우와_함께_센다() {
    limiter.recordFailure("203.0.113.9");

    then(counter).should().increment(KEY, Duration.ofMinutes(10));
  }

  @Test
  void Redis_장애면_통과시키고_기록_실패도_던지지_않는다() {
    given(counter.count(KEY)).willThrow(new RedisConnectionFailureException("down"));
    willThrow(new RedisConnectionFailureException("down"))
        .given(counter)
        .increment(KEY, Duration.ofMinutes(10));

    assertThatCode(() -> limiter.checkAllowed("203.0.113.9")).doesNotThrowAnyException();
    assertThatCode(() -> limiter.recordFailure("203.0.113.9")).doesNotThrowAnyException();
  }
}
