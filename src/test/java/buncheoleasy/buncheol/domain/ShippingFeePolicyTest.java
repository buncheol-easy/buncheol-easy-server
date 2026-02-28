package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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
            assertThatCode(() -> ShippingFeePolicy.of(3000, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void cu_배송비만_설정해도_생성에_성공한다() {
            // when & then
            assertThatCode(() -> ShippingFeePolicy.of(null, 2500))
                    .doesNotThrowAnyException();
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
        @ValueSource(ints = {0, -1, -1000})
        void gs25_배송비가_0_이하면_예외가_발생한다(int fee) {
            // when & then
            assertThatThrownBy(() -> ShippingFeePolicy.of(fee, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_SHIPPING_FEE_INVALID);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -1000})
        void cu_배송비가_0_이하면_예외가_발생한다(int fee) {
            // when & then
            assertThatThrownBy(() -> ShippingFeePolicy.of(null, fee))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_SHIPPING_FEE_INVALID);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 500, 3000})
        void 양수_배송비는_유효하다(int fee) {
            // when & then
            assertThatCode(() -> ShippingFeePolicy.of(fee, null))
                    .doesNotThrowAnyException();
        }
    }
}
