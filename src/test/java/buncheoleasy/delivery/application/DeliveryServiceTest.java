package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
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
  private static final Instant NOW = Instant.parse("2026-03-23T12:00:00Z");

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
      given(buncheol.getStatus()).willReturn(BuncheolStatus.CONFIRMED);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willDoNothing().given(buncheol).validateOwner(HOST_ID);

      // when
      deliveryService.registerTracking(HOST_ID, DELIVERY_ID, "TRACK123");

      // then
      then(deliveryDomainService).should().registerTracking(DELIVERY_ID, "TRACK123", NOW);
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

      then(deliveryDomainService).should(never()).registerTracking(anyLong(), anyString(), any());
    }

    @Test
    void 분철이_진행확정_전이면_예외가_발생한다() {
      // 모집중 발송을 허용하면 마감 시점 취소(최소 인원 미달)와 이미 발송된 물건이 모순되므로 막는다.
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willDoNothing().given(buncheol).validateOwner(HOST_ID);

      // when & then
      assertThatThrownBy(() -> deliveryService.registerTracking(HOST_ID, DELIVERY_ID, "TRACK123"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_BUNCHEOL_NOT_CONFIRMED);

      then(deliveryDomainService).should(never()).registerTracking(anyLong(), anyString(), any());
      then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void CAS_전이가_실패하면_이벤트를_발행하지_않는다() {
      // 웹훅 자동 전이가 먼저 DELIVERED/RECEIVED 로 진행시킨 경합 케이스 — 상태 위반으로 끝나야 한다.
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.CONFIRMED);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willDoNothing().given(buncheol).validateOwner(HOST_ID);
      willThrow(new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID))
          .given(deliveryDomainService)
          .registerTracking(DELIVERY_ID, "TRACK123", NOW);

      // when & then
      assertThatThrownBy(() -> deliveryService.registerTracking(HOST_ID, DELIVERY_ID, "TRACK123"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);

      then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void 관리자가_운송장을_정상_등록한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.CONFIRMED);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      // when
      deliveryService.registerTrackingByAdmin(DELIVERY_ID, "TRACK123");

      // then
      then(deliveryDomainService).should().registerTracking(DELIVERY_ID, "TRACK123", NOW);
      then(eventPublisher).should().publishEvent(any(TrackingRegisteredEvent.class));
    }

    @Test
    void 관리자여도_분철이_진행확정_전이면_예외가_발생한다() {
      // 관리자 경로를 열어두면 "모집중엔 배송중 참여가 없다" 불변식이 깨지므로 동일하게 막는다.
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      // when & then
      assertThatThrownBy(() -> deliveryService.registerTrackingByAdmin(DELIVERY_ID, "TRACK123"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_BUNCHEOL_NOT_CONFIRMED);

      then(deliveryDomainService).should(never()).registerTracking(anyLong(), anyString(), any());
      then(eventPublisher).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("수령 확인 테스트")
  class ConfirmReceiptTest {

    @Test
    void 참여자_본인이_수령_확인한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getParticipantId()).willReturn(PARTICIPANT_ID);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when
      deliveryService.confirmReceipt(PARTICIPANT_ID, DELIVERY_ID);

      // then
      then(deliveryDomainService).should().confirmReceipt(DELIVERY_ID, NOW);
    }

    @Test
    void 관리자는_참여자_검증_없이_수령완료로_전이한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);

      // when
      deliveryService.confirmReceiptByAdmin(DELIVERY_ID);

      // then
      then(deliveryDomainService).should().confirmReceipt(DELIVERY_ID, NOW);
    }

    @Test
    void 관리자여도_운송장_등록_전이면_예외가_발생한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      willThrow(new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID))
          .given(deliveryDomainService)
          .confirmReceipt(DELIVERY_ID, NOW);

      // when & then
      assertThatThrownBy(() -> deliveryService.confirmReceiptByAdmin(DELIVERY_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }

    @Test
    void 참여자_본인이어도_운송장_등록_전이면_예외가_발생한다() {
      // given
      Delivery delivery = createSnapshotDelivery();
      Participation participation = mock(Participation.class);
      given(participation.getParticipantId()).willReturn(PARTICIPANT_ID);

      given(deliveryDomainService.getDelivery(DELIVERY_ID)).willReturn(delivery);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      willThrow(new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID))
          .given(deliveryDomainService)
          .confirmReceipt(DELIVERY_ID, NOW);

      // when & then
      assertThatThrownBy(() -> deliveryService.confirmReceipt(PARTICIPANT_ID, DELIVERY_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
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

      then(deliveryDomainService).should(never()).confirmReceipt(anyLong(), any());
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
