package buncheoleasy.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.user.domain.shipping.ShippingMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DeliveryTrackerCarriers 단위 테스트")
class DeliveryTrackerCarriersTest {

  @Nested
  @DisplayName("carrierId — 배송 방법 → 캐리어 ID")
  class CarrierIdTest {

    @Test
    void CU반값택배는_cupost_로_매핑된다() {
      assertThat(DeliveryTrackerCarriers.carrierId(ShippingMethod.CU_HALF)).isEqualTo("kr.cupost");
    }

    @Test
    void GS25반값택배는_cvsnet_으로_매핑된다() {
      assertThat(DeliveryTrackerCarriers.carrierId(ShippingMethod.GS25_HALF))
          .isEqualTo("kr.cvsnet");
    }
  }

  @Nested
  @DisplayName("shippingMethod — 캐리어 ID 역매핑")
  class ShippingMethodTest {

    @Test
    void 지원_캐리어는_배송_방법으로_역매핑된다() {
      assertThat(DeliveryTrackerCarriers.shippingMethod("kr.cupost"))
          .contains(ShippingMethod.CU_HALF);
      assertThat(DeliveryTrackerCarriers.shippingMethod("kr.cvsnet"))
          .contains(ShippingMethod.GS25_HALF);
    }

    @Test
    void 미지원_캐리어는_empty_를_반환한다() {
      assertThat(DeliveryTrackerCarriers.shippingMethod("kr.cjlogistics")).isEmpty();
    }

    @Test
    void null_이면_empty_를_반환한다() {
      assertThat(DeliveryTrackerCarriers.shippingMethod(null)).isEmpty();
    }
  }
}
