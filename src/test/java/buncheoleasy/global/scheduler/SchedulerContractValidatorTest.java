package buncheoleasy.global.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchedulerContractValidator 단위 테스트")
class SchedulerContractValidatorTest {

  private final Clock clock = Clock.system(ZoneOffset.UTC);

  private SchedulerActivationGate gateWithGrace(final Duration grace) {
    return new SchedulerActivationGate(clock, grace);
  }

  @Test
  void initial_delay_가_유예와_마진의_합보다_크면_통과한다() {
    assertThatCode(
            () -> new SchedulerContractValidator(gateWithGrace(Duration.ofSeconds(300)), 420_000L))
        .doesNotThrowAnyException();
  }

  @Test
  void initial_delay_가_유예_이하면_기동을_막는다() {
    // 위반은 조용한 장애(12h 주기 스케줄러 영구 미실행)라 fail-fast 가 계약이다 (docs/39).
    assertThatIllegalStateException()
        .isThrownBy(
            () -> new SchedulerContractValidator(gateWithGrace(Duration.ofSeconds(500)), 420_000L))
        .withMessageContaining("TRACKING_REFRESH_INITIAL_DELAY_MS");
  }

  @Test
  void 유예보다_크더라도_마진_안이면_위반이다() {
    // 두 시계의 원점이 다르다(initial-delay 는 refresh, 유예는 ready 기점) — "유예보다 1초 큼"
    // 은 러너 실행 시간만큼 실여유가 없어 같은 함정이다 (#92 8차 리뷰).
    assertThatIllegalStateException()
        .isThrownBy(
            () -> new SchedulerContractValidator(gateWithGrace(Duration.ofSeconds(300)), 301_000L));
  }

  @Test
  void 경계값_동일은_위반이다() {
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                new SchedulerContractValidator(
                    gateWithGrace(Duration.ofMillis(360_000 - SchedulerContractValidator.MIN_MARGIN_MS)),
                    360_000L));
  }
}
