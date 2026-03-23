package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.domain.PaymentPhase;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.PhoneNumber;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationPaymentHandler 단위 테스트")
class ParticipationPaymentHandlerTest {

  @InjectMocks private ParticipationPaymentHandler participationPaymentHandler;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private ShippingAddressDomainService shippingAddressDomainService;
  @Mock private UserDomainService userDomainService;

  @Spy
  private Clock clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneId.of("Asia/Seoul"));

  private static final Long PARTICIPATION_ID = 1L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long BUNCHEOL_MEMBER_ID = 10L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;

  private Participation createInstantParticipation() {
    Participation participation =
        Participation.createInstant(
            1L, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, 50_000L);
    setId(participation, PARTICIPATION_ID);
    return participation;
  }

  private Participation createBidParticipation() {
    Participation participation =
        Participation.createBid(
            1L, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, 30_000L);
    setId(participation, PARTICIPATION_ID);
    return participation;
  }

  private void stubDeliverySnapshot() {
    ShippingAddress shippingAddress =
        new ShippingAddress(
            SHIPPING_ADDRESS_ID,
            PARTICIPANT_ID,
            ShippingMethod.GS25_HALF,
            "GS25 강남점",
            LocalDateTime.now(),
            LocalDateTime.now());
    given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID))
        .willReturn(shippingAddress);

    User user = User.create("KAKAO", "kakao123", "test@test.com");
    setFieldValue(user, "nickname", Nickname.of("TestUser"));
    setFieldValue(user, "phoneNumber", PhoneNumber.of("01012345678"));
    given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(user);

    given(deliveryDomainService.createDelivery(any(Delivery.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  @Nested
  @DisplayName("참여 객체 소유권 검증 테스트")
  class ValidateOwnershipTest {

    @Test
    void 소유자이면_예외가_발생하지_않는다() {
      // given
      Participation participation = createInstantParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when & then (예외 없이 완료)
      participationPaymentHandler.validateOwnership(PARTICIPATION_ID, PARTICIPANT_ID);
    }

    @Test
    void 소유자가_아니면_예외가_발생한다() {
      // given
      Participation participation = createInstantParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when & then
      assertThatThrownBy(
              () -> participationPaymentHandler.validateOwnership(PARTICIPATION_ID, 999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_NO_PERMISSION);
    }
  }

  @Nested
  @DisplayName("결제 완료 처리 테스트")
  class OnPaymentCompletedTest {

    @Test
    void 즉시_구매_결제_완료시_참여가_확정되고_제시가_일괄_실패_처리된다() {
      // given
      Participation participation = createInstantParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when
      participationPaymentHandler.onPaymentCompleted(PARTICIPATION_ID, PaymentPhase.INSTANT);

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(participation.getFinalizedAt()).isNotNull();
      then(participationDomainService)
          .should()
          .updateParticipationStatus(participation, ParticipationStatus.PAYMENT_PENDING);
      then(participationDomainService)
          .should()
          .failAllOpenBids(eq(BUNCHEOL_MEMBER_ID), any(String.class));
      then(deliveryDomainService).should(never()).createDelivery(any());
    }

    @Test
    void 예치금_결제_완료시_제시가_활성화된다() {
      // given
      Participation participation = createBidParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when
      participationPaymentHandler.onPaymentCompleted(PARTICIPATION_ID, PaymentPhase.DEPOSIT);

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.ACTIVE_BID);
      then(participationDomainService)
          .should()
          .updateParticipationStatus(participation, ParticipationStatus.PAYMENT_PENDING);
      then(participationDomainService).should(never()).failAllOpenBids(any(), any());
      then(deliveryDomainService).should(never()).createDelivery(any());
    }

    @Test
    void 잔금_결제_완료시_참여가_확정된다() {
      // given
      Participation participation = createBidParticipation();
      setStatus(participation, ParticipationStatus.AWAITING_BALANCE_PAYMENT);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      stubDeliverySnapshot();

      // when
      participationPaymentHandler.onPaymentCompleted(PARTICIPATION_ID, PaymentPhase.BALANCE);

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(participation.getFinalizedAt()).isNotNull();
      then(participationDomainService)
          .should()
          .updateParticipationStatus(participation, ParticipationStatus.AWAITING_BALANCE_PAYMENT);
      then(participationDomainService).should(never()).failAllOpenBids(any(), any());
    }
  }

  @Nested
  @DisplayName("배송 스냅샷 생성 테스트")
  class DeliverySnapshotTest {

    @Test
    void 즉시_구매_결제_완료시_배송_스냅샷이_생성되지_않는다() {
      // INSTANT 확정 참여의 배송 스냅샷은 분철 마감 시점에 일괄 생성
      // given
      Participation participation = createInstantParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when
      participationPaymentHandler.onPaymentCompleted(PARTICIPATION_ID, PaymentPhase.INSTANT);

      // then
      then(deliveryDomainService).should(never()).createDelivery(any());
    }

    @Test
    void 잔금_결제_완료시_배송_스냅샷이_생성된다() {
      // given
      Participation participation = createBidParticipation();
      setStatus(participation, ParticipationStatus.AWAITING_BALANCE_PAYMENT);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      stubDeliverySnapshot();

      // when
      participationPaymentHandler.onPaymentCompleted(PARTICIPATION_ID, PaymentPhase.BALANCE);

      // then
      ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);
      then(deliveryDomainService).should().createDelivery(deliveryCaptor.capture());

      Delivery delivery = deliveryCaptor.getValue();
      assertThat(delivery.getParticipationId()).isEqualTo(PARTICIPATION_ID);
      assertThat(delivery.getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
      assertThat(delivery.getStoreName()).isEqualTo("GS25 강남점");
      assertThat(delivery.getReceiverNickname()).isEqualTo("TestUser");
      assertThat(delivery.getReceiverPhoneNumber()).isEqualTo("01012345678");
    }

    @Test
    void 예치금_결제_완료시_배송_스냅샷이_생성되지_않는다() {
      // given
      Participation participation = createBidParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when
      participationPaymentHandler.onPaymentCompleted(PARTICIPATION_ID, PaymentPhase.DEPOSIT);

      // then
      then(deliveryDomainService).should(never()).createDelivery(any());
      then(shippingAddressDomainService).should(never()).getShippingAddress(any());
      then(userDomainService).should(never()).getUser(any());
    }
  }

  @Nested
  @DisplayName("결제 실패 처리 테스트")
  class OnPaymentFailedTest {

    @Test
    void 즉시_구매_결제_실패시_참여가_실패_처리된다() {
      // given
      Participation participation = createInstantParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when
      participationPaymentHandler.onPaymentFailed(PARTICIPATION_ID, PaymentPhase.INSTANT, "결제 실패");

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.FAILED);
      assertThat(participation.getFailReason()).isEqualTo("결제 실패");
      assertThat(participation.getFinalizedAt()).isNotNull();
      then(participationDomainService)
          .should()
          .updateParticipationStatus(participation, ParticipationStatus.PAYMENT_PENDING);
    }

    @Test
    void 예치금_결제_실패시_참여가_실패_처리된다() {
      // given
      Participation participation = createBidParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when
      participationPaymentHandler.onPaymentFailed(PARTICIPATION_ID, PaymentPhase.DEPOSIT, "결제 실패");

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.FAILED);
      assertThat(participation.getFailReason()).isEqualTo("결제 실패");
      assertThat(participation.getFinalizedAt()).isNotNull();
      then(participationDomainService)
          .should()
          .updateParticipationStatus(participation, ParticipationStatus.PAYMENT_PENDING);
    }

    @Test
    void 잔금_결제_실패시_참여_상태를_변경하지_않는다() {
      // when
      participationPaymentHandler.onPaymentFailed(
          PARTICIPATION_ID, PaymentPhase.BALANCE, "잔금 결제 실패");

      // then
      then(participationDomainService).should(never()).getParticipation(any());
      then(participationDomainService).should(never()).updateParticipationStatus(any(), any());
    }
  }

  private void setId(final Participation participation, final Long id) {
    setFieldValue(participation, "id", id);
  }

  private void setStatus(final Participation participation, final ParticipationStatus status) {
    setFieldValue(participation, "status", status);
  }

  private void setFieldValue(final Object target, final String fieldName, final Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
