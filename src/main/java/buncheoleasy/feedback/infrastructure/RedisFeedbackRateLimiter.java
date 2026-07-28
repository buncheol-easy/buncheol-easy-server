package buncheoleasy.feedback.infrastructure;

import buncheoleasy.feedback.domain.FeedbackRateLimiter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 윈도우 카운터 기반 제한. 첫 제출 때 카운터에 TTL 을 걸고, 윈도우 안의 제출 횟수가 한도를 넘으면 거부한다.
 *
 * <p>고정 윈도우라 경계에서 최대 2배까지 통과할 수 있지만, 도배를 막는 것이 목적이라 정밀도보다 단순함을 택했다.
 */
@Component
public class RedisFeedbackRateLimiter implements FeedbackRateLimiter {

  private static final String KEY_PREFIX = "FB:";

  private final StringRedisTemplate redisTemplate;
  private final int maxSubmissions;
  private final Duration window;

  public RedisFeedbackRateLimiter(
      final StringRedisTemplate redisTemplate,
      @Value("${app.feedback.rate-limit.max-submissions:5}") final int maxSubmissions,
      @Value("${app.feedback.rate-limit.window:10m}") final Duration window) {
    this.redisTemplate = redisTemplate;
    this.maxSubmissions = maxSubmissions;
    this.window = window;
  }

  @Override
  public void checkAndRecord(final String clientKey) {
    String key = KEY_PREFIX + clientKey;
    Long count = redisTemplate.opsForValue().increment(key);

    // 첫 제출(카운터 신규 생성)에만 TTL 을 건다 — 매번 걸면 윈도우가 계속 연장돼 영구 차단이 된다.
    if (count != null && count == 1L) {
      redisTemplate.expire(key, window.toSeconds(), TimeUnit.SECONDS);
    }
    if (count != null && count > maxSubmissions) {
      throw new BusinessException(ErrorCode.FEEDBACK_RATE_LIMITED);
    }
  }
}
