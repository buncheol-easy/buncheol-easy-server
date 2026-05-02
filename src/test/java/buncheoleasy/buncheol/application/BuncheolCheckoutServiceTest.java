package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.application.PaymentService;
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
  private static final long BID_AMOUNT = 30_000L;

  private static Participation newParticipation() {
    return Participation.create(BUNCHEOL_ID, 10L, PARTICIPANT_ID, 200L, BID_AMOUNT);
  }

  @Nested
  @DisplayName("참여 신청 테스트")
  class ParticipateTest {

    @Test
    void 참여_신청에_성공하면_결제_주문은_생성하지_않고_참여_객체만_반환한다() {
      ParticipateRequest request = new ParticipateRequest(10L, 200L, BID_AMOUNT);

      Participation participation = newParticipation();
      setId(participation, PARTICIPATION_ID);

      given(buncheolParticipationService.createParticipation(BUNCHEOL_ID, PARTICIPANT_ID, request))
          .willReturn(participation);

      Participation result =
          buncheolCheckoutService.participate(BUNCHEOL_ID, PARTICIPANT_ID, request);

      assertThat(result).isSameAs(participation);
    }
  }

  @Nested
  @DisplayName("낙찰자 결제 주문 생성 테스트")
  class StartPaymentCheckoutTest {

    @Test
    void 결제_주문은_제시_금액으로_생성된다() {
      Participation participation = newParticipation();
      setId(participation, PARTICIPATION_ID);
      setFieldValue(participation, "status", ParticipationStatus.AWAITING_PAYMENT);

      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      PaymentOrderInfo paymentOrderInfo =
          new PaymentOrderInfo(
              "clientKey", "order_123", "분철 낙찰자 결제", BID_AMOUNT, "http://success", "http://fail");
      given(paymentService.createPaymentOrder(eq(PARTICIPATION_ID), eq(BID_AMOUNT), anyString()))
          .willReturn(paymentOrderInfo);

      PaymentOrderInfo result =
          buncheolCheckoutService.startPaymentCheckout(PARTICIPANT_ID, PARTICIPATION_ID);

      assertThat(result).isSameAs(paymentOrderInfo);
    }

    @Test
    void 참여자가_다르면_예외가_발생한다() {
      Long wrongUserId = 999L;
      Participation participation = newParticipation();
      setId(participation, PARTICIPATION_ID);

      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      assertThatThrownBy(
              () -> buncheolCheckoutService.startPaymentCheckout(wrongUserId, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }

    @Test
    void AWAITING_PAYMENT_상태가_아니면_예외가_발생한다() {
      Participation participation = newParticipation();
      setId(participation, PARTICIPATION_ID);
      // ACTIVE_BID 상태 (기본)

      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      assertThatThrownBy(
              () -> buncheolCheckoutService.startPaymentCheckout(PARTICIPANT_ID, PARTICIPATION_ID))
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
