package buncheoleasy.user.domain.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingAddressDomainService 단위 테스트")
class ShippingAddressDomainServiceTest {

  @InjectMocks private ShippingAddressDomainService shippingAddressDomainService;

  @Mock private ShippingAddressRepository shippingAddressRepository;

  private ShippingAddress savedAddress(Long id, Long userId, String method, String storeName) {
    return new ShippingAddress(
        id, userId, ShippingMethod.of(method), storeName, LocalDateTime.now(), LocalDateTime.now());
  }

  @Nested
  @DisplayName("배송지 생성 테스트")
  class CreateShippingAddressTest {

    @Test
    void 배송지를_정상적으로_생성할_수_있다() {
      // given
      Long userId = 1L;
      given(shippingAddressRepository.countByUserId(userId)).willReturn(0);
      given(
              shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
                  userId, "GS25_HALF", "GS25 강남역점"))
          .willReturn(false);
      given(shippingAddressRepository.save(any(ShippingAddress.class)))
          .willAnswer(inv -> inv.getArgument(0));

      // when
      ShippingAddress result =
          shippingAddressDomainService.createShippingAddress(userId, "GS25_HALF", "GS25 강남역점");

      // then
      assertThat(result.getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
      assertThat(result.getStoreName()).isEqualTo("GS25 강남역점");
      then(shippingAddressRepository).should().save(any(ShippingAddress.class));
    }

    @Test
    void 배송지가_10개이면_예외가_발생한다() {
      // given
      Long userId = 1L;
      given(shippingAddressRepository.countByUserId(userId)).willReturn(10);

      // when & then
      assertThatThrownBy(
              () ->
                  shippingAddressDomainService.createShippingAddress(
                      userId, "GS25_HALF", "GS25 강남역점"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED);

      then(shippingAddressRepository).should(never()).save(any());
    }

    @Test
    void 중복된_배송지이면_예외가_발생한다() {
      // given
      Long userId = 1L;
      given(shippingAddressRepository.countByUserId(userId)).willReturn(3);
      given(
              shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
                  userId, "GS25_HALF", "GS25 강남역점"))
          .willReturn(true);

      // when & then
      assertThatThrownBy(
              () ->
                  shippingAddressDomainService.createShippingAddress(
                      userId, "GS25_HALF", "GS25 강남역점"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_DUPLICATE);

      then(shippingAddressRepository).should(never()).save(any());
    }
  }

  @Nested
  @DisplayName("배송지 수정 테스트")
  class UpdateShippingAddressTest {

    @Test
    void 배송지를_정상적으로_수정할_수_있다() {
      // given: 더티체킹에 위임하므로 repository 의 명시적 update 호출은 없음
      Long userId = 1L;
      Long addressId = 10L;
      ShippingAddress address = savedAddress(addressId, userId, "GS25_HALF", "GS25 강남역점");
      given(shippingAddressRepository.findById(addressId)).willReturn(Optional.of(address));
      given(
              shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
                  userId, "CU_HALF", "CU 홍대입구점"))
          .willReturn(false);

      // when
      shippingAddressDomainService.updateShippingAddress(userId, addressId, "CU_HALF", "CU 홍대입구점");

      // then
      assertThat(address.getShippingMethod()).isEqualTo(ShippingMethod.CU_HALF);
      assertThat(address.getStoreName()).isEqualTo("CU 홍대입구점");
    }

    @Test
    void 존재하지_않는_배송지를_수정하면_예외가_발생한다() {
      // given
      given(shippingAddressRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () ->
                  shippingAddressDomainService.updateShippingAddress(
                      1L, 999L, "CU_HALF", "CU 홍대입구점"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
    }

    @Test
    void 다른_유저의_배송지를_수정하면_예외가_발생하고_엔티티는_변경되지_않는다() {
      // given
      Long addressId = 10L;
      ShippingAddress address = savedAddress(addressId, 2L, "GS25_HALF", "GS25 강남역점");
      given(shippingAddressRepository.findById(addressId)).willReturn(Optional.of(address));

      // when & then: userId=1L이지만 배송지 소유자는 2L
      assertThatThrownBy(
              () ->
                  shippingAddressDomainService.updateShippingAddress(
                      1L, addressId, "CU_HALF", "CU 홍대입구점"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);

      assertThat(address.getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
      assertThat(address.getStoreName()).isEqualTo("GS25 강남역점");
    }

    @Test
    void 다른_내용으로_수정할_때_이미_존재하는_배송지이면_예외가_발생하고_엔티티는_변경되지_않는다() {
      // given
      Long userId = 1L;
      Long addressId = 10L;
      ShippingAddress address = savedAddress(addressId, userId, "GS25_HALF", "GS25 강남역점");
      given(shippingAddressRepository.findById(addressId)).willReturn(Optional.of(address));
      given(
              shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
                  userId, "CU_HALF", "CU 홍대입구점"))
          .willReturn(true);

      // when & then
      assertThatThrownBy(
              () ->
                  shippingAddressDomainService.updateShippingAddress(
                      userId, addressId, "CU_HALF", "CU 홍대입구점"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_DUPLICATE);

      assertThat(address.getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
      assertThat(address.getStoreName()).isEqualTo("GS25 강남역점");
    }
  }

  @Nested
  @DisplayName("배송지 삭제 테스트")
  class DeleteShippingAddressTest {

    @Test
    void 본인_배송지를_삭제할_수_있다() {
      // given
      Long userId = 1L;
      Long addressId = 10L;
      ShippingAddress address = savedAddress(addressId, userId, "GS25_HALF", "GS25 강남역점");
      given(shippingAddressRepository.findById(addressId)).willReturn(Optional.of(address));

      // when
      shippingAddressDomainService.deleteShippingAddress(userId, addressId);

      // then
      then(shippingAddressRepository).should().delete(addressId);
    }

    @Test
    void 존재하지_않는_배송지를_삭제하면_예외가_발생한다() {
      // given
      given(shippingAddressRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> shippingAddressDomainService.deleteShippingAddress(1L, 999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);

      then(shippingAddressRepository).should(never()).delete(any());
    }

    @Test
    void 다른_유저의_배송지를_삭제하면_예외가_발생한다() {
      // given
      Long addressId = 10L;
      ShippingAddress address = savedAddress(addressId, 2L, "GS25_HALF", "GS25 강남역점");
      given(shippingAddressRepository.findById(addressId)).willReturn(Optional.of(address));

      // when & then
      assertThatThrownBy(() -> shippingAddressDomainService.deleteShippingAddress(1L, addressId))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);

      then(shippingAddressRepository).should(never()).delete(any());
    }
  }

  @Nested
  @DisplayName("배송지 조회 테스트")
  class GetUserShippingAddressesTest {

    @Test
    void 유저의_배송지_목록을_조회할_수_있다() {
      // given
      Long userId = 1L;
      List<ShippingAddress> expected =
          List.of(
              savedAddress(1L, userId, "GS25_HALF", "GS25 강남역점"),
              savedAddress(2L, userId, "CU_HALF", "CU 홍대입구점"));
      given(shippingAddressRepository.getUserShippingAddresses(userId)).willReturn(expected);

      // when
      List<ShippingAddress> result = shippingAddressDomainService.getUserShippingAddresses(userId);

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    void 배송지가_없으면_빈_리스트를_반환한다() {
      // given
      Long userId = 1L;
      given(shippingAddressRepository.getUserShippingAddresses(userId)).willReturn(List.of());

      // when
      List<ShippingAddress> result = shippingAddressDomainService.getUserShippingAddresses(userId);

      // then
      assertThat(result).isEmpty();
    }
  }
}
