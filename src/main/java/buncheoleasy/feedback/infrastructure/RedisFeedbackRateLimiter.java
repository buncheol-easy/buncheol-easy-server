package buncheoleasy.feedback.infrastructure;

import buncheoleasy.feedback.domain.FeedbackRateLimiter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.ratelimit.FixedWindowRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 의견 보내기 제한. 카운팅은 {@link FixedWindowRateLimiter} 에 위임하고, 여기서는 <b>정책</b>(두 겹 제한과 거부 시 에러)만 정한다.
 *
 * <p>제한은 <b>두 겹</b>이다.
 *
 * <ol>
 *   <li>제출 주체별(회원 ID 또는 IP) — 한 사람이 도배하는 것을 막는다.
 *   <li>전역 — 클라이언트가 헤더를 조작해 주체 키를 매번 바꾸더라도 슬랙에 흘러드는 총량을 묶는다. 엔드포인트가 permitAll 이라 인증 계층의 백스톱이 없어
 *       필요하다.
 * </ol>
 *
 * <p>위임 대상의 fail-open 계약(저장소 장애 시 통과)을 그대로 받아들인다 — 이 엔드포인트의 목적은 "정확히 제한하는 것"이 아니라 "의견을 받는
 * 것"이고, 뒤의 슬랙 발송도 비동기 + 웹훅 미설정 가드로 이미 느슨하다. 제한 장치 장애로 의견을 잃는 쪽이 손해가 크다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFeedbackRateLimiter implements FeedbackRateLimiter {

  private static final String KEY_PREFIX = "FB:";
  private static final String GLOBAL_KEY = KEY_PREFIX + "global";

  private final FixedWindowRateLimiter rateLimiter;
  private final FeedbackRateLimitProperties properties;

  @Override
  public void checkAndRecord(final String clientKey) {
    if (!rateLimiter.tryAcquire(
        GLOBAL_KEY, properties.globalMaxSubmissions(), properties.window())) {
      log.warn("의견 전역 제출 한도 초과 - 접수를 거부한다");
      throw new BusinessException(ErrorCode.FEEDBACK_RATE_LIMITED);
    }
    if (!rateLimiter.tryAcquire(
        KEY_PREFIX + clientKey, properties.maxSubmissions(), properties.window())) {
      throw new BusinessException(ErrorCode.FEEDBACK_RATE_LIMITED);
    }
  }
}
