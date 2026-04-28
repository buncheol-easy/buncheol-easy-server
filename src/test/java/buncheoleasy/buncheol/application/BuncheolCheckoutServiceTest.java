package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationPolicy;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationType;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.application.PaymentService;
import buncheoleasy.payment.domain.PaymentPhase;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolCheckoutService 단위 테스트")
class BuncheolCheckoutServiceTest {

  @InjectMocks private BuncheolCheckoutService buncheolCheckoutService;

  @Mock private BuncheolParticipationService buncheolParticipationService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private PaymentService paymentService;

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long PARTICIPATION_ID = 50L;

  private PaymentOrderInfo dummyPaymentOrderInfo() {
    return new PaymentOrderInfo(
        "clientKey", "order_123", "결제 이름", 50_000L, "http://success", "http://fail");
  }

  @Nested
  @DisplayName("참여 신청 및 결제 주문 생성 테스트")
  class StartCheckoutTest {

    @Test
    void 즉시_구매_참여_신청_및_결제_주문_생성에_성공한다() {
      // given
      ParticipateRequest request =
          new ParticipateRequest(10L, 200L, ParticipationType.INSTANT, null);

      Participation participation =
          Participation.createInstant(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 50_000L);
      setId(participation, PARTICIPATION_ID);

      given(buncheolParticipationService.createParticipation(BUNCHEOL_ID, PARTICIPANT_ID, request))
          .willReturn(participation);

      PaymentOrderInfo paymentOrderInfo = dummyPaymentOrderInfo();
      given(
              paymentService.createPaymentOrder(
                  eq(PARTICIPATION_ID), eq(PaymentPhase.INSTANT), eq(50_000L), anyString()))
          .willReturn(paymentOrderInfo);

      // when
      ParticipationCheckoutInfo result =
          buncheolCheckoutService.startCheckout(BUNCHEOL_ID, PARTICIPANT_ID, request);

      // then
      assertThat(result.participation()).isSameAs(participation);
      assertThat(result.paymentOrder()).isSameAs(paymentOrderInfo);
      then(paymentService)
          .should()
          .createPaymentOrder(
              eq(PARTICIPATION_ID), eq(PaymentPhase.INSTANT), eq(50_000L), anyString());
    }

    @Test
    void 제시_참여_신청시_예치금으로_결제_주문을_생성한다() {
      // given
      ParticipateRequest request =
          new ParticipateRequest(10L, 200L, ParticipationType.BID, 30_000L);

      Participation participation =
          Participation.createBid(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 30_000L);
      setId(participation, PARTICIPATION_ID);

      given(buncheolParticipationService.createParticipation(BUNCHEOL_ID, PARTICIPANT_ID, request))
          .willReturn(participation);

      long expectedDeposit = ParticipationPolicy.resolveBidDepositAmount(30_000L);
      PaymentOrderInfo paymentOrderInfo = dummyPaymentOrderInfo();
      given(
              paymentService.createPaymentOrder(
                  eq(PARTICIPATION_ID), eq(PaymentPhase.DEPOSIT), eq(expectedDeposit), anyString()))
          .willReturn(paymentOrderInfo);

      // when
      ParticipationCheckoutInfo result =
          buncheolCheckoutService.startCheckout(BUNCHEOL_ID, PARTICIPANT_ID, request);

      // then
      assertThat(result.participation()).isSameAs(participation);
      then(paymentService)
          .should()
          .createPaymentOrder(
              eq(PARTICIPATION_ID), eq(PaymentPhase.DEPOSIT), eq(expectedDeposit), anyString());
    }
  }

  @Nested
  @DisplayName("잔금 결제 주문 생성 테스트")
  class StartBalancePaymentCheckoutTest {

    @Test
    void 잔금_결제_주문_생성에_성공한다() {
      // given
      Participation participation =
          Participation.createBid(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 30_000L);
      setId(participation, PARTICIPATION_ID);
      setFieldValue(
          participation,
          "status",
          ParticipationStatus.AWAITING_BALANCE_PAYMENT);
      setFieldValue(participation, "balanceDueAmount", 25_000L);

      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      PaymentOrderInfo paymentOrderInfo = dummyPaymentOrderInfo();
      given(
              paymentService.createPaymentOrder(
                  eq(PARTICIPATION_ID), eq(PaymentPhase.BALANCE), eq(25_000L), anyString()))
          .willReturn(paymentOrderInfo);

      // when
      PaymentOrderInfo result =
          buncheolCheckoutService.startBalancePaymentCheckout(PARTICIPANT_ID, PARTICIPATION_ID);

      // then
      assertThat(result).isSameAs(paymentOrderInfo);
    }

    @Test
    void 참여자가_다르면_예외가_발생한다() {
      // given
      Long wrongUserId = 999L;
      Participation participation =
          Participation.createBid(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 30_000L);
      setId(participation, PARTICIPATION_ID);

      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolCheckoutService.startBalancePaymentCheckout(
                      wrongUserId, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }

    @Test
    void AWAITING_BALANCE_PAYMENT_상태가_아니면_예외가_발생한다() {
      // given
      Participation participation =
          Participation.createBid(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 30_000L);
      setId(participation, PARTICIPATION_ID);
      // PAYMENT_PENDING 상태 (기본)

      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolCheckoutService.startBalancePaymentCheckout(
                      PARTICIPANT_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED);
    }

    @Test
    void 잔금이_null이면_예외가_발생한다() {
      // given
      Participation participation =
          Participation.createBid(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 30_000L);
      setId(participation, PARTICIPATION_ID);
      setFieldValue(
          participation,
          "status",
          ParticipationStatus.AWAITING_BALANCE_PAYMENT);
      // balanceDueAmount is null by default

      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolCheckoutService.startBalancePaymentCheckout(
                      PARTICIPANT_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED);
    }

    @Test
    void 잔금이_0_이하이면_예외가_발생한다() {
      // given
      Participation participation =
          Participation.createBid(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, 30_000L);
      setId(participation, PARTICIPATION_ID);
      setFieldValue(
          participation,
          "status",
          ParticipationStatus.AWAITING_BALANCE_PAYMENT);
      setFieldValue(participation, "balanceDueAmount", 0L);

      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // when & then
      assertThatThrownBy(
              () ->
                  buncheolCheckoutService.startBalancePaymentCheckout(
                      PARTICIPANT_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED);
    }
  }

  private void setId(final Participation participation, final Long id) {
    setFieldValue(participation, "id", id);
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
