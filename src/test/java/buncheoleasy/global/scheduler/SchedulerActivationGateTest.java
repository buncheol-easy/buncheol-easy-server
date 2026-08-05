package buncheoleasy.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchedulerActivationGate 단위 테스트")
class SchedulerActivationGateTest {

  private static final Instant BOOT = Instant.parse("2026-08-04T03:00:00Z");

  // 시각을 단계별로 진행시키기 위한 가변 Clock.
  private final AtomicReference<Instant> now = new AtomicReference<>(BOOT);
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now.get();
        }
      };

  @Test
  void ready_전에는_비활성이다() {
    SchedulerActivationGate gate = new SchedulerActivationGate(clock, Duration.ofSeconds(300));

    assertThat(gate.isActive()).isFalse();
  }

  @Test
  void ready_후_유예가_지나기_전에는_비활성이다() {
    SchedulerActivationGate gate = new SchedulerActivationGate(clock, Duration.ofSeconds(300));
    gate.markReady();

    now.set(BOOT.plusSeconds(299));

    assertThat(gate.isActive()).isFalse();
  }

  @Test
  void ready_후_유예가_지나면_활성이다() {
    SchedulerActivationGate gate = new SchedulerActivationGate(clock, Duration.ofSeconds(300));
    gate.markReady();

    now.set(BOOT.plusSeconds(300));

    assertThat(gate.isActive()).isTrue();
  }

  @Test
  void 유예값이_null_이면_기본_300초로_동작한다() {
    // @Value 인라인 기본값은 키 부재만 커버한다 — 빈 env 값("")이 null 로 오는 경로의 명시 폴백 검증.
    SchedulerActivationGate gate = new SchedulerActivationGate(clock, null);
    gate.markReady();

    now.set(BOOT.plusSeconds(299));
    assertThat(gate.isActive()).isFalse();

    now.set(BOOT.plusSeconds(300));
    assertThat(gate.isActive()).isTrue();
  }
}
