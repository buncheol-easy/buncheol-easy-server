package buncheoleasy.user.domain.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ShippingAddress 도메인 테스트")
class ShippingAddressTest {

    @Nested
    @DisplayName("ShippingAddress 생성 테스트")
    class CreateTest {

        @Test
        void GS25_반값택배_배송지를_생성할_수_있다() {
            // given
            Long userId = 1L;

            // when
            ShippingAddress address = ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점");

            // then
            assertThat(address.getUserId()).isEqualTo(userId);
            assertThat(address.getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
            assertThat(address.getStoreName()).isEqualTo("GS25 강남역점");
            assertThat(address.getId()).isNull();
        }

        @Test
        void CU_반값택배_배송지를_생성할_수_있다() {
            // given
            Long userId = 1L;

            // when
            ShippingAddress address = ShippingAddress.create(userId, "CU_HALF", "CU 홍대입구점");

            // then
            assertThat(address.getShippingMethod()).isEqualTo(ShippingMethod.CU_HALF);
            assertThat(address.getStoreName()).isEqualTo("CU 홍대입구점");
        }

        @Test
        void 유효하지_않은_배송방법으로_생성하면_예외가_발생한다() {
            assertThatThrownBy(() -> ShippingAddress.create(1L, "INVALID", "GS25 강남역점"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SHIPPING_METHOD_FORMAT_INVALID);
        }
    }

    @Nested
    @DisplayName("ShippingAddress 수정 테스트")
    class UpdateTest {

        @Test
        void 배송방법과_지점명을_변경할_수_있다() {
            // given
            ShippingAddress address = ShippingAddress.create(1L, "GS25_HALF", "GS25 강남역점");

            // when
            address.update("CU_HALF", "CU 홍대입구점");

            // then
            assertThat(address.getShippingMethod()).isEqualTo(ShippingMethod.CU_HALF);
            assertThat(address.getStoreName()).isEqualTo("CU 홍대입구점");
        }

        @Test
        void 동일한_배송방법으로도_수정할_수_있다() {
            // given
            ShippingAddress address = ShippingAddress.create(1L, "GS25_HALF", "GS25 강남역점");

            // when
            address.update("GS25_HALF", "GS25 신촌역점");

            // then
            assertThat(address.getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
            assertThat(address.getStoreName()).isEqualTo("GS25 신촌역점");
        }

        @Test
        void 유효하지_않은_배송방법으로_수정하면_예외가_발생한다() {
            // given
            ShippingAddress address = ShippingAddress.create(1L, "GS25_HALF", "GS25 강남역점");

            // when & then
            assertThatThrownBy(() -> address.update("INVALID_METHOD", "GS25 강남역점"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SHIPPING_METHOD_FORMAT_INVALID);
        }
    }

    @Nested
    @DisplayName("isSameAddress 테스트")
    class IsSameAddressTest {

        @Test
        void 동일한_배송방법과_지점명이면_true를_반환한다() {
            // given
            ShippingAddress address = ShippingAddress.create(1L, "GS25_HALF", "GS25 강남역점");

            // when & then
            assertThat(address.isSameAddress("GS25_HALF", "GS25 강남역점")).isTrue();
        }

        @Test
        void 배송방법이_다르면_false를_반환한다() {
            // given
            ShippingAddress address = ShippingAddress.create(1L, "GS25_HALF", "GS25 강남역점");

            // when & then
            assertThat(address.isSameAddress("CU_HALF", "GS25 강남역점")).isFalse();
        }

        @Test
        void 지점명이_다르면_false를_반환한다() {
            // given
            ShippingAddress address = ShippingAddress.create(1L, "GS25_HALF", "GS25 강남역점");

            // when & then
            assertThat(address.isSameAddress("GS25_HALF", "GS25 신촌역점")).isFalse();
        }
    }

    @Nested
    @DisplayName("isOwnedBy 테스트")
    class IsOwnedByTest {

        @Test
        void 소유자_ID와_일치하면_true를_반환한다() {
            // given
            Long userId = 1L;
            ShippingAddress address = ShippingAddress.create(userId, "GS25_HALF", "GS25 강남역점");

            // when & then
            assertThat(address.isOwnedBy(userId)).isTrue();
        }

        @Test
        void 소유자_ID와_일치하지_않으면_false를_반환한다() {
            // given
            ShippingAddress address = ShippingAddress.create(1L, "GS25_HALF", "GS25 강남역점");

            // when & then
            assertThat(address.isOwnedBy(2L)).isFalse();
        }
    }
}
