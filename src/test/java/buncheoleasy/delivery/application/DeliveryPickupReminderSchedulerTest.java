package buncheoleasy.delivery.application;

import static org.mockito.BDDMockito.then;

import buncheoleasy.global.scheduler.SchedulerActivationGate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryPickupReminderScheduler 단위 테스트")
class DeliveryPickupReminderSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-08-04T03:00:00Z");

  @InjectMocks private DeliveryPickupReminderScheduler deliveryPickupReminderScheduler;

  @Mock private DeliveryPickupReminderService deliveryPickupReminderService;

  @Mock private SchedulerActivationGate schedulerActivationGate;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void 기동_유예_중에는_독촉을_시도하지_않는다() {
    // 하루 1회 cron 이라 skip 되면 그날 발송이 없다 — 게이트 차단 자체가 계약임을 고정한다
    // (블루-그린: 전환 전 인스턴스의 발송 마킹 소진·알림 방지, docs/39).
    BDDMockito.given(schedulerActivationGate.isActive()).willReturn(false);

    deliveryPickupReminderScheduler.remindUnclaimedDeliveries();

    then(deliveryPickupReminderService).shouldHaveNoInteractions();
  }
}
