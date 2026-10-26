package buncheoleasy.delivery.application;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerException;
import buncheoleasy.global.scheduler.SchedulerActivationGate;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

  @Mock private TrackingSyncService trackingSyncService;

  @Mock private SchedulerActivationGate schedulerActivationGate;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void 기동_유예_중에는_독촉을_시도하지_않는다() {
    // 하루 1회 cron 이라 skip 되면 그날 발송이 없다 — 게이트 차단 자체가 계약임을 고정한다
    // (블루-그린: 전환 전 인스턴스의 발송 마킹 소진·알림 방지, docs/39).
    given(schedulerActivationGate.isActive()).willReturn(false);

    deliveryPickupReminderScheduler.remindUnclaimedDeliveries();

    then(deliveryPickupReminderService).shouldHaveNoInteractions();
    then(trackingSyncService).shouldHaveNoInteractions();
  }

  @Test
  void 독촉_전에_운송장마다_한_번씩_추적을_동기화한다() {
    // given
    given(schedulerActivationGate.isActive()).willReturn(true);
    given(deliveryPickupReminderService.findReminderTargets(NOW))
        .willReturn(
            List.of(
                delivery(1L, ShippingMethod.CU_HALF, "CU-1"),
                delivery(2L, ShippingMethod.CU_HALF, "CU-1"),
                delivery(3L, ShippingMethod.GS25_HALF, "GS-1")));

    // when
    deliveryPickupReminderScheduler.remindUnclaimedDeliveries();

    // then
    InOrder inOrder = inOrder(trackingSyncService, deliveryPickupReminderService);
    inOrder.verify(trackingSyncService, times(1)).sync("kr.cupost", "CU-1");
    inOrder.verify(trackingSyncService, times(1)).sync("kr.cvsnet", "GS-1");
    inOrder.verify(deliveryPickupReminderService).remind(1L, NOW);
    inOrder.verify(deliveryPickupReminderService).remind(2L, NOW);
    inOrder.verify(deliveryPickupReminderService).remind(3L, NOW);
  }

  @Test
  void 추적_동기화가_실패해도_독촉은_진행한다() {
    // given
    given(schedulerActivationGate.isActive()).willReturn(true);
    given(deliveryPickupReminderService.findReminderTargets(NOW))
        .willReturn(List.of(delivery(1L, ShippingMethod.CU_HALF, "CU-1")));
    willThrow(new DeliveryTrackerException("통신 오류"))
        .given(trackingSyncService)
        .sync("kr.cupost", "CU-1");

    // when
    deliveryPickupReminderScheduler.remindUnclaimedDeliveries();

    // then
    then(deliveryPickupReminderService).should().remind(1L, NOW);
  }

  private Delivery delivery(
      final Long id, final ShippingMethod shippingMethod, final String trackingNumber) {
    Delivery delivery =
        Delivery.createSnapshot(id, null, shippingMethod, "테스트점", "테스트유저", "01012345678");
    setField(delivery, "id", id);
    setField(delivery, "trackingNumber", trackingNumber);
    return delivery;
  }

  private void setField(final Object target, final String fieldName, final Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
