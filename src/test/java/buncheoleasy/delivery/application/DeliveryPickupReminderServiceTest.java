package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryPickupReminderService 단위 테스트")
class DeliveryPickupReminderServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-23T12:00:00Z");
  private static final Duration THRESHOLD = Duration.ofHours(24);
  private static final Long DELIVERY_ID = 10L;

  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private DeliveryPickupReminderService deliveryPickupReminderService;

  @BeforeEach
  void setUp() {
    deliveryPickupReminderService =
        new DeliveryPickupReminderService(deliveryDomainService, eventPublisher, THRESHOLD);
  }

  @Test
  void 독촉_대상은_도착_후_기준_시간이_지난_배송을_조회한다() {
    // given
    Delivery delivery =
        Delivery.createSnapshot(20L, ShippingMethod.GS25_HALF, "GS25 강남점", "테스트유저", "01012345678");
    given(deliveryDomainService.findPickupReminderTargets(NOW.minus(THRESHOLD), 200))
        .willReturn(List.of(delivery));

    // when & then
    assertThat(deliveryPickupReminderService.findReminderTargets(NOW)).containsExactly(delivery);
  }

  @Test
  void 독촉_마킹에_성공하면_알림_이벤트를_발행한다() {
    // given
    given(deliveryDomainService.markPickupReminderSent(DELIVERY_ID, NOW)).willReturn(true);

    // when & then
    assertThat(deliveryPickupReminderService.remind(DELIVERY_ID, NOW)).isTrue();
    then(eventPublisher).should().publishEvent(new PickupReminderDueEvent(DELIVERY_ID));
  }

  @Test
  void 이미_독촉했거나_수령된_배송은_이벤트를_발행하지_않는다() {
    // given
    given(deliveryDomainService.markPickupReminderSent(DELIVERY_ID, NOW)).willReturn(false);

    // when & then
    assertThat(deliveryPickupReminderService.remind(DELIVERY_ID, NOW)).isFalse();
    then(eventPublisher).shouldHaveNoInteractions();
  }
}
