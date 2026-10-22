package buncheoleasy.feedback.infrastructure;

import buncheoleasy.feedback.domain.FeedbackRateLimiter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.infrastructure.redis.RedisFixedWindowCounter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 윈도우 카운터 기반 제한. 고정 윈도우라 경계에서 최대 2배까지 통과할 수 있지만, 도배를 막는 것이 목적이라 정밀도보다 단순함을 택했다.
 *
 * <p>제한은 <b>두 겹</b>이다.
 *
 * <ol>
 *   <li>제출 주체별(회원 ID 또는 IP) — 한 사람이 도배하는 것을 막는다.
 *   <li>전역 — 클라이언트가 헤더를 조작해 주체 키를 매번 바꾸더라도 슬랙에 흘러드는 총량을 묶는다. 엔드포인트가 permitAll 이라 인증 계층의 백스톱이 없어
 *       필요하다.
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFeedbackRateLimiter implements FeedbackRateLimiter {

  private static final String KEY_PREFIX = "FB:";
  private static final String GLOBAL_KEY = KEY_PREFIX + "global";

  private final RedisFixedWindowCounter counter;
  private final FeedbackRateLimitProperties properties;

  @Override
  public void checkAndRecord(final String clientKey) {
    Duration window = properties.window();

    if (increment(GLOBAL_KEY, window) > properties.globalMaxSubmissions()) {
      log.warn("의견 전역 제출 한도 초과 - 접수를 거부한다");
      throw new BusinessException(ErrorCode.FEEDBACK_RATE_LIMITED);
    }
    if (increment(KEY_PREFIX + clientKey, window) > properties.maxSubmissions()) {
      throw new BusinessException(ErrorCode.FEEDBACK_RATE_LIMITED);
    }
  }

  /**
   * 카운터를 1 올리고 현재 값을 돌려준다.
   *
   * <p><b>fail-open</b>: Redis 가 죽었다고 의견 접수까지 막지는 않는다. 이 엔드포인트의 목적은 "정확히 제한하는 것"이 아니라 "의견을 받는
   * 것"이고, 뒤의 슬랙 발송도 비동기 + 웹훅 미설정 가드로 이미 느슨하게 처리돼 있다. 제한 장치 장애로 의견을 잃는 쪽이 손해가 크다.
   */
  private long increment(final String key, final Duration window) {
    try {
      return counter.increment(key, window);
    } catch (DataAccessException e) {
      log.warn("의견 제출 제한 확인 실패 - 제한 없이 통과시킨다 - key={}", key, e);
      return 0L;
    }
  }
}
