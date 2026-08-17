package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ShippingFeePolicy 도메인 테스트")
class ShippingFeePolicyTest {

  @Nested
  @DisplayName("ShippingFeePolicy 생성 테스트")
  class CreateTest {

    @Test
    void gs25_배송비만_설정해도_생성에_성공한다() {
      // when & then
      assertThatCode(() -> ShippingFeePolicy.of(3000, null)).doesNotThrowAnyException();
    }

    @Test
    void cu_배송비만_설정해도_생성에_성공한다() {
      // when & then
      assertThatCode(() -> ShippingFeePolicy.of(null, 2500)).doesNotThrowAnyException();
    }

    @Test
    void gs25와_cu_배송비_모두_설정하면_생성에_성공한다() {
      // given
      ShippingFeePolicy policy = ShippingFeePolicy.of(3000, 2500);

      // then
      assertThat(policy.gs25ShippingFee()).isEqualTo(3000);
      assertThat(policy.cuShippingFee()).isEqualTo(2500);
    }

    @Test
    void gs25와_cu_배송비가_모두_null이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> ShippingFeePolicy.of(null, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_SHIPPING_FEE_REQUIRED);
    }
  }

  @Nested
  @DisplayName("배송비 값 검증 테스트")
  class ValidateFeeValueTest {

    @ParameterizedTest
    @ValueSource(ints = {-1, -1000})
    void gs25_배송비가_음수면_예외가_발생한다(int fee) {
      // when & then
      assertThatThrownBy(() -> ShippingFeePolicy.of(fee, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_SHIPPING_FEE_INVALID);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -1000})
    void cu_배송비가_음수면_예외가_발생한다(int fee) {
      // when & then
      assertThatThrownBy(() -> ShippingFeePolicy.of(null, fee))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_SHIPPING_FEE_INVALID);
    }

    // 0원은 개최자가 배송비를 받지 않는 무료 배송으로 허용한다.
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 500, 3000})
    void gs25_배송비가_0_이상이면_유효하다(int fee) {
      // when & then
      assertThatCode(() -> ShippingFeePolicy.of(fee, null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 500, 3000})
    void cu_배송비가_0_이상이면_유효하다(int fee) {
      // when & then
      assertThatCode(() -> ShippingFeePolicy.of(null, fee)).doesNotThrowAnyException();
    }

    @Test
    void 양쪽_모두_0원이어도_미입력과_구분되어_생성에_성공한다() {
      // 최소 1개 입력 검증(validateAtLeastOneFeeProvided)은 null 기준이라 0원 두 개는 정상 통과해야 한다.
      assertThatCode(() -> ShippingFeePolicy.of(0, 0)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("무료배송 판정 테스트")
  class IsFreeTest {

    @Test
    void 양쪽_배송비가_모두_0원이면_무료배송이다() {
      assertThat(ShippingFeePolicy.of(0, 0).isFree()).isTrue();
    }

    @Test
    void 등록한_배송수단의_배송비가_0원이면_미등록_배송수단이_있어도_무료배송이다() {
      assertThat(ShippingFeePolicy.of(0, null).isFree()).isTrue();
      assertThat(ShippingFeePolicy.of(null, 0).isFree()).isTrue();
    }

    @Test
    void 한쪽만_0원이면_무료배송이_아니다() {
      assertThat(ShippingFeePolicy.of(0, 3000).isFree()).isFalse();
      assertThat(ShippingFeePolicy.of(3000, 0).isFree()).isFalse();
    }

    @Test
    void 양쪽_배송비가_모두_유료면_무료배송이_아니다() {
      assertThat(ShippingFeePolicy.of(3000, 2500).isFree()).isFalse();
    }
  }

  @Nested
  @DisplayName("배송수단별 배송비 조회 테스트")
  class FeeForTest {

    @Test
    void GS25_배송수단의_배송비를_반환한다() {
      ShippingFeePolicy policy = ShippingFeePolicy.of(3000, 2500);

      assertThat(policy.feeFor(ShippingMethod.GS25_HALF)).isEqualTo(3000);
    }

    @Test
    void CU_배송수단의_배송비를_반환한다() {
      ShippingFeePolicy policy = ShippingFeePolicy.of(3000, 2500);

      assertThat(policy.feeFor(ShippingMethod.CU_HALF)).isEqualTo(2500);
    }

    @Test
    void 정책이_지원하지_않는_배송수단이면_예외가_발생한다() {
      // CU 배송비를 설정하지 않은 정책에 CU 배송수단으로 조회
      ShippingFeePolicy policy = ShippingFeePolicy.of(3000, null);

      assertThatThrownBy(() -> policy.feeFor(ShippingMethod.CU_HALF))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_SHIPPING_METHOD_NOT_SUPPORTED);
    }
  }
}
