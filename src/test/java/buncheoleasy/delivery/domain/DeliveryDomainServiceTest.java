package buncheoleasy.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryDomainService 단위 테스트")
class DeliveryDomainServiceTest {

  @InjectMocks private DeliveryDomainService deliveryDomainService;

  @Mock private DeliveryRepository deliveryRepository;

  private Delivery createDelivery() {
    return Delivery.createSnapshot(
        1L, ShippingMethod.GS25_HALF, "GS25 강남점", "테스트유저", "01012345678");
  }

  @Nested
  @DisplayName("createDelivery 테스트")
  class CreateDeliveryTest {

    @Test
    void 배송_정보를_정상_저장한다() {
      // given
      Delivery delivery = createDelivery();
      given(deliveryRepository.save(delivery)).willReturn(delivery);

      // when
      Delivery result = deliveryDomainService.createDelivery(delivery);

      // then
      assertThat(result).isEqualTo(delivery);
      then(deliveryRepository).should().save(delivery);
    }
  }

  @Nested
  @DisplayName("getDelivery 테스트")
  class GetDeliveryTest {

    @Test
    void 존재하는_배송_정보를_반환한다() {
      // given
      Delivery delivery = createDelivery();
      given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));

      // when
      Delivery result = deliveryDomainService.getDelivery(1L);

      // then
      assertThat(result).isEqualTo(delivery);
    }

    @Test
    void 존재하지_않으면_예외가_발생한다() {
      // given
      given(deliveryRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> deliveryDomainService.getDelivery(999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("getDeliveryByParticipationId 테스트")
  class GetDeliveryByParticipationIdTest {

    @Test
    void 참여_ID로_배송_정보를_반환한다() {
      // given
      Delivery delivery = createDelivery();
      given(deliveryRepository.findByParticipationId(1L)).willReturn(Optional.of(delivery));

      // when
      Delivery result = deliveryDomainService.getDeliveryByParticipationId(1L);

      // then
      assertThat(result).isEqualTo(delivery);
    }

    @Test
    void 존재하지_않으면_예외가_발생한다() {
      // given
      given(deliveryRepository.findByParticipationId(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> deliveryDomainService.getDeliveryByParticipationId(999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("updateDeliveryStatus 테스트")
  class UpdateDeliveryStatusTest {

    @Test
    void 상태_업데이트가_성공하면_예외가_발생하지_않는다() {
      // given
      Delivery delivery = createDelivery();
      given(deliveryRepository.updateStatus(delivery, DeliveryStatus.SNAPSHOTTED)).willReturn(true);

      // when & then (예외 없이 완료)
      deliveryDomainService.updateDeliveryStatus(delivery, DeliveryStatus.SNAPSHOTTED);
    }

    @Test
    void 상태_업데이트가_실패하면_예외가_발생한다() {
      // given
      Delivery delivery = createDelivery();
      given(deliveryRepository.updateStatus(delivery, DeliveryStatus.SNAPSHOTTED))
          .willReturn(false);

      // when & then
      assertThatThrownBy(
              () ->
                  deliveryDomainService.updateDeliveryStatus(delivery, DeliveryStatus.SNAPSHOTTED))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }
  }
}
