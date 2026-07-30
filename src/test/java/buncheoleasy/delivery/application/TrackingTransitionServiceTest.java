package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.delivery.domain.DeliveryDomainService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingTransitionService 단위 테스트")
class TrackingTransitionServiceTest {

  private static final Long DELIVERY_ID = 10L;
  private static final Instant EVENT_TIME = Instant.parse("2026-03-23T12:00:00Z");
  private static final Instant NOW = Instant.parse("2026-03-23T12:30:00Z");

  @InjectMocks private TrackingTransitionService trackingTransitionService;

  @Mock private DeliveryDomainService deliveryDomainService;

  @Nested
  @DisplayName("markDelivered — 지점 도착 감지")
  class MarkDeliveredTest {

    @Test
    void SHIPPING_배송을_DELIVERED_로_전이한다() {
      // given
      given(deliveryDomainService.markDeliveredIfShipping(DELIVERY_ID, EVENT_TIME, NOW))
          .willReturn(true);

      // when & then
      assertThat(trackingTransitionService.markDelivered(DELIVERY_ID, EVENT_TIME, NOW)).isTrue();
    }
  }

  @Nested
  @DisplayName("markReceived — 고객 수령 감지")
  class MarkReceivedTest {

    @Test
    void DELIVERED_배송을_RECEIVED_로_전이한다() {
      // given
      given(deliveryDomainService.markReceivedIfDelivered(DELIVERY_ID, EVENT_TIME, NOW))
          .willReturn(true);

      // when & then
      assertThat(trackingTransitionService.markReceived(DELIVERY_ID, EVENT_TIME, NOW)).isTrue();
      then(deliveryDomainService)
          .should(never())
          .markReceivedIfShipping(DELIVERY_ID, EVENT_TIME, NOW);
    }

    @Test
    void 도착_감지를_놓친_SHIPPING_배송은_직행_전이로_폴백한다() {
      // given
      given(deliveryDomainService.markReceivedIfDelivered(DELIVERY_ID, EVENT_TIME, NOW))
          .willReturn(false);
      given(deliveryDomainService.markReceivedIfShipping(DELIVERY_ID, EVENT_TIME, NOW))
          .willReturn(true);

      // when & then
      assertThat(trackingTransitionService.markReceived(DELIVERY_ID, EVENT_TIME, NOW)).isTrue();
    }

    @Test
    void 이미_수령완료된_배송은_둘_다_실패해_false_를_돌려준다() {
      // given
      given(deliveryDomainService.markReceivedIfDelivered(DELIVERY_ID, EVENT_TIME, NOW))
          .willReturn(false);
      given(deliveryDomainService.markReceivedIfShipping(DELIVERY_ID, EVENT_TIME, NOW))
          .willReturn(false);

      // when & then
      assertThat(trackingTransitionService.markReceived(DELIVERY_ID, EVENT_TIME, NOW)).isFalse();
    }
  }
}
