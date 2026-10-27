package buncheoleasy.auth.infrastructure.redis;

import buncheoleasy.auth.domain.AppleLoginAttemptLimiter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.infrastructure.redis.RedisFixedWindowCounter;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * IP 별 애플 토큰 검증 실패 수를 고정 윈도우로 센다. Redis 장애 시에는 통과시킨다(fail-open) — 제한 장치 장애로 로그인 자체를 막는 손해가 더 크고,
 * 토큰 검증은 그대로 돈다.
 */
@Slf4j
@Component
public class RedisAppleLoginAttemptLimiter implements AppleLoginAttemptLimiter {

  private static final String KEY_PREFIX = "APPLE_LOGIN:fail:ip:";

  private final RedisFixedWindowCounter counter;
  private final int maxFailures;
  private final Duration window;

  public RedisAppleLoginAttemptLimiter(
      final RedisFixedWindowCounter counter,
      @Value("${app.apple.signin.rate-limit.max-failures:10}") final int maxFailures,
      // 단위를 붙이지 않으면 '분'으로 읽는다 — Spring 기본 단위(밀리초)로 해석되면 제한이 사라진다.
      @DurationUnit(ChronoUnit.MINUTES) @Value("${app.apple.signin.rate-limit.window:10m}")
          final Duration window) {
    this.counter = counter;
    this.maxFailures = maxFailures;
    this.window = window;
  }

  @Override
  public void checkAllowed(final String clientIp) {
    long failures;
    try {
      failures = counter.count(KEY_PREFIX + clientIp);
    } catch (DataAccessException exception) {
      log.warn("애플 로그인 실패 수 확인 실패 - 제한 없이 통과시킨다", exception);
      return;
    }
    if (failures >= maxFailures) {
      throw new BusinessException(ErrorCode.AUTH_APPLE_LOGIN_RATE_LIMITED);
    }
  }

  @Override
  public void recordFailure(final String clientIp) {
    try {
      counter.increment(KEY_PREFIX + clientIp, window);
    } catch (DataAccessException exception) {
      log.warn("애플 로그인 실패 기록 실패", exception);
    }
  }
}
