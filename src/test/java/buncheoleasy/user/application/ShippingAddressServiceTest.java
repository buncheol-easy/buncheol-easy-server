package buncheoleasy.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import buncheoleasy.user.dto.request.ShippingAddressRequest;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingAddressService 단위 테스트")
class ShippingAddressServiceTest {

    @InjectMocks
    private ShippingAddressService shippingAddressService;

    @Mock
    private ShippingAddressDomainService shippingAddressDomainService;

    private ShippingAddress savedAddress(Long id, Long userId, String method, String storeName) {
        return new ShippingAddress(id, userId, ShippingMethod.of(method), storeName,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("배송지 등록 테스트")
    class RegisterShippingAddressTest {

        @Test
        void 배송지_등록을_도메인_서비스에_위임한다() {
            // given
            Long userId = 1L;
            ShippingAddressRequest request = new ShippingAddressRequest("GS25_HALF", "GS25 강남역점");

            // when
            shippingAddressService.registerShippingAddress(userId, request);

            // then
            then(shippingAddressDomainService).should()
                    .createShippingAddress(userId, "GS25_HALF", "GS25 강남역점");
        }
    }

    @Nested
    @DisplayName("배송지 수정 테스트")
    class ModifyShippingAddressTest {

        @Test
        void 배송지_수정을_도메인_서비스에_위임한다() {
            // given
            Long userId = 1L;
            Long addressId = 10L;
            ShippingAddressRequest request = new ShippingAddressRequest("CU_HALF", "CU 홍대입구점");

            // when
            shippingAddressService.modifyShippingAddress(userId, addressId, request);

            // then
            then(shippingAddressDomainService).should()
                    .updateShippingAddress(userId, addressId, "CU_HALF", "CU 홍대입구점");
        }
    }

    @Nested
    @DisplayName("배송지 삭제 테스트")
    class RemoveShippingAddressTest {

        @Test
        void 배송지_삭제를_도메인_서비스에_위임한다() {
            // given
            Long userId = 1L;
            Long addressId = 10L;

            // when
            shippingAddressService.removeShippingAddress(userId, addressId);

            // then
            then(shippingAddressDomainService).should()
                    .deleteShippingAddress(userId, addressId);
        }
    }

    @Nested
    @DisplayName("배송지 조회 테스트")
    class GetUserShippingAddressesTest {

        @Test
        void 배송지_목록을_response로_변환하여_반환한다() {
            // given
            Long userId = 1L;
            List<ShippingAddress> addresses = List.of(
                    savedAddress(1L, userId, "GS25_HALF", "GS25 강남역점"),
                    savedAddress(2L, userId, "CU_HALF", "CU 홍대입구점")
            );
            given(shippingAddressDomainService.getUserShippingAddresses(userId)).willReturn(addresses);

            // when
            List<ShippingAddressResponse> responses = shippingAddressService.getUserShippingAddresses(userId);

            // then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).id()).isEqualTo(1L);
            assertThat(responses.get(0).shippingMethod()).isEqualTo("GS25_HALF");
            assertThat(responses.get(0).storeName()).isEqualTo("GS25 강남역점");
            assertThat(responses.get(1).id()).isEqualTo(2L);
            assertThat(responses.get(1).shippingMethod()).isEqualTo("CU_HALF");
            assertThat(responses.get(1).storeName()).isEqualTo("CU 홍대입구점");
        }

        @Test
        void 배송지가_없으면_빈_리스트를_반환한다() {
            // given
            Long userId = 1L;
            given(shippingAddressDomainService.getUserShippingAddresses(userId)).willReturn(List.of());

            // when
            List<ShippingAddressResponse> responses = shippingAddressService.getUserShippingAddresses(userId);

            // then
            assertThat(responses).isEmpty();
        }
    }
}
