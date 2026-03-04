package buncheoleasy.user.domain.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ShippingMethod 테스트")
class ShippingMethodTest {

  @Nested
  @DisplayName("ShippingMethod 생성 테스트")
  class OfTest {

    @Test
    void GS25_반값택배로_ShippingMethod를_생성할_수_있다() {
      ShippingMethod method = ShippingMethod.of("GS25_HALF");
      assertThat(method).isEqualTo(ShippingMethod.GS25_HALF);
    }

    @Test
    void CU_반값택배로_ShippingMethod를_생성할_수_있다() {
      ShippingMethod method = ShippingMethod.of("CU_HALF");
      assertThat(method).isEqualTo(ShippingMethod.CU_HALF);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "gs25_half", "CU_HALF_PRICE", "GS25", "cu_half", "GS25HALF"})
    void 유효하지_않은_배송방법이면_예외가_발생한다(String name) {
      assertThatThrownBy(() -> ShippingMethod.of(name))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_METHOD_FORMAT_INVALID);
    }
  }
}
