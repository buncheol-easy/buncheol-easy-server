package buncheoleasy.notification.infrastructure;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 입금 기한 임박 알림의 중복 발송 가드. Redis {@code SETNX} 로 참여별 1회 발송을 보장한다. 폴링이 같은 대상을 여러 번 집어와도, 다중 인스턴스가 동시에
 * 처리해도 최초 1건만 통과한다. TTL 은 입금 윈도우(약 4h)로 두어 기한이 지나면 자연 소멸한다.
 */
@Component
@RequiredArgsConstructor
public class DueReminderGuard {

  private static final String KEY_PREFIX = "alimtalk:due-reminder:";
  private static final Duration TTL = Duration.ofHours(4);

  private final StringRedisTemplate redisTemplate;

  /** 최초 호출이면 true(발송 진행), 이미 발송 마킹돼 있으면 false. */
  public boolean tryMark(final Long participationId) {
    Boolean acquired =
        redisTemplate.opsForValue().setIfAbsent(buildKey(participationId), "1", TTL);
    return Boolean.TRUE.equals(acquired);
  }

  private String buildKey(final Long participationId) {
    return KEY_PREFIX + participationId;
  }
}
