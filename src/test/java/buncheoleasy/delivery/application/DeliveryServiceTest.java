package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryService 단위 테스트")
class DeliveryServiceTest {

  @InjectMocks private DeliveryService deliveryService;

  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-23T12:00:00Z"), ZoneOffset.UTC);

  private static final Long HOST_ID = 1L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long DELIVERY_ID = 10L;
  private static final Long PARTICIPATION_ID = 20L;
  private static final Long BUNCHEOL_ID = 30L;

  private Delivery createSnapshotDelivery() {
    Delivery delivery =
        Delivery.createSnapshot(
            PARTICIPATION_ID, ShippingMethod.GS25_HALF, "GS25 강남점", "테스트유저", "01012345678");
    setField(delivery, "id", DELIVERY_ID);
    return delivery;
  }

  @Nested
  @DisplayName("운송장 등록 테스트")
  class RegisterTrackingTest {

    @Test
    void 개최자가_운송장을_정상_등록한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      Buncheol buncheol = mock(Buncheol.class);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willDoNothing().given(buncheol).validateOwner(HOST_ID);

      // when
      deliveryService.registerTracking(HOST_ID, DELIVERY_ID, "TRACK123");

      // then
      assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
      assertThat(delivery.getTrackingNumber()).isEqualTo("TRACK123");
      then(eventPublisher).should().publishEvent(any(TrackingRegisteredEvent.class));
    }

    @Test
    void 개최자가_아니면_예외가_발생한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      Buncheol buncheol = mock(Buncheol.class);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(999L);

      // when & then
      assertThatThrownBy(() -> deliveryService.registerTracking(999L, DELIVERY_ID, "TRACK123"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);
    }
  }

  @Nested
  @DisplayName("수령 확인 테스트")
  class ConfirmReceiptTest {

    @Test
    void 참여자_본인이_수령_확인한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      delivery.registerTracking("TRACK123", Instant.now(clock));
      Participation participation = mock(Participation.class);
      given(participation.getParticipantId()).willReturn(PARTICIPANT_ID);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when
      deliveryService.confirmReceipt(PARTICIPANT_ID, DELIVERY_ID);

      // then
      assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
      assertThat(delivery.getReceivedAt()).isNotNull();
    }

    @Test
    void 참여자_본인이_아니면_예외가_발생한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getParticipantId()).willReturn(PARTICIPANT_ID);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when & then
      assertThatThrownBy(() -> deliveryService.confirmReceipt(999L, DELIVERY_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_NO_PERMISSION);
    }
  }

  private void setField(final Object target, final String fieldName, final Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
