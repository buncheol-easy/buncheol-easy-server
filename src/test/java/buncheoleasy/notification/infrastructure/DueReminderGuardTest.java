package buncheoleasy.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("DueReminderGuard 단위 테스트")
class DueReminderGuardTest {

  @InjectMocks private DueReminderGuard dueReminderGuard;

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @Test
  @DisplayName("최초 마킹(SETNX 성공)이면 true 를 반환한다")
  void firstMarkReturnsTrue() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(eq("alimtalk:due-reminder:50"), eq("1"), any(Duration.class)))
        .willReturn(true);

    assertThat(dueReminderGuard.tryMark(50L)).isTrue();
  }

  @Test
  @DisplayName("이미 마킹돼 있으면(SETNX 실패) false 를 반환한다")
  void alreadyMarkedReturnsFalse() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).willReturn(false);

    assertThat(dueReminderGuard.tryMark(50L)).isFalse();
  }
}
