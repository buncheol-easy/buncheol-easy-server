package buncheoleasy.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient.CallThrottle;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeliveryTrackerClient.CallThrottle 단위 테스트")
class DeliveryTrackerCallThrottleTest {

  private static final Duration INTERVAL = Duration.ofMillis(200);
  private static final long INTERVAL_NANOS = INTERVAL.toNanos();

  @Test
  void 첫_호출은_대기_없이_통과한다() {
    CallThrottle throttle = new CallThrottle(INTERVAL, () -> 0L);

    assertThat(throttle.reserveWaitNanos()).isLessThanOrEqualTo(0);
  }

  @Test
  void 간격_안에_연달아_호출하면_다음_슬롯까지_대기시간을_돌려준다() {
    // 시계가 멈춘 상태의 연속 호출 = 최악의 연사 — n번째 호출은 n-1 간격만큼 밀린다.
    CallThrottle throttle = new CallThrottle(INTERVAL, () -> 0L);

    throttle.reserveWaitNanos();
    assertThat(throttle.reserveWaitNanos()).isEqualTo(INTERVAL_NANOS);
    assertThat(throttle.reserveWaitNanos()).isEqualTo(INTERVAL_NANOS * 2);
  }

  @Test
  void 간격이_지난_뒤_호출은_대기_없이_통과한다() {
    AtomicLong clock = new AtomicLong(0);
    CallThrottle throttle = new CallThrottle(INTERVAL, clock::get);

    throttle.reserveWaitNanos();
    clock.addAndGet(INTERVAL_NANOS);

    assertThat(throttle.reserveWaitNanos()).isLessThanOrEqualTo(0);
  }

  @Test
  void 오래_쉬었다_호출해도_밀린_슬롯이_누적되지_않는다() {
    // 유휴 기간이 크레딧으로 쌓이면 다음 배치가 다시 연사된다 — 슬롯은 항상 현재 시각 기준으로 예약돼야 한다.
    AtomicLong clock = new AtomicLong(0);
    CallThrottle throttle = new CallThrottle(INTERVAL, clock::get);

    throttle.reserveWaitNanos();
    clock.addAndGet(INTERVAL_NANOS * 100);

    assertThat(throttle.reserveWaitNanos()).isLessThanOrEqualTo(0);
    assertThat(throttle.reserveWaitNanos()).isEqualTo(INTERVAL_NANOS);
  }
}
