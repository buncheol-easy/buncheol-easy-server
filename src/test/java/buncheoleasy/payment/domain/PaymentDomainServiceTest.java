package buncheoleasy.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentDomainService 단위 테스트")
class PaymentDomainServiceTest {

  @InjectMocks private PaymentDomainService paymentDomainService;

  @Mock private PaymentRepository paymentRepository;

  @Nested
  @DisplayName("결제 생성 테스트")
  class CreateTest {

    @Test
    void 결제_생성에_성공한다() {
      // given
      Payment payment = Payment.createPayment(1L, PaymentPhase.INSTANT, "order_123", 50_000L);
      given(paymentRepository.save(payment)).willReturn(payment);

      // when
      Payment result = paymentDomainService.create(payment);

      // then
      assertThat(result).isSameAs(payment);
    }
  }

  @Nested
  @DisplayName("PENDING 상태 결제 업데이트 테스트")
  class UpdatePendingPaymentTest {

    @Test
    void PENDING_상태_결제_업데이트에_성공한다() {
      // given
      Payment payment = Payment.createPayment(1L, PaymentPhase.INSTANT, "order_123", 50_000L);
      given(paymentRepository.update(payment, PaymentStatus.PENDING)).willReturn(true);

      // when & then (예외 없이 완료)
      paymentDomainService.updatePendingPayment(payment);
      then(paymentRepository).should().update(payment, PaymentStatus.PENDING);
    }

    @Test
    void 업데이트_실패시_예외가_발생한다() {
      // given
      Payment payment = Payment.createPayment(1L, PaymentPhase.INSTANT, "order_123", 50_000L);
      given(paymentRepository.update(payment, PaymentStatus.PENDING)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> paymentDomainService.updatePendingPayment(payment))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("CONFIRMING 상태 결제 업데이트 테스트")
  class UpdateConfirmingPaymentTest {

    @Test
    void CONFIRMING_상태_결제_업데이트에_성공한다() {
      // given
      Payment payment = Payment.createPayment(1L, PaymentPhase.INSTANT, "order_123", 50_000L);
      given(paymentRepository.update(payment, PaymentStatus.CONFIRMING)).willReturn(true);

      // when & then
      paymentDomainService.updateConfirmingPayment(payment);
      then(paymentRepository).should().update(payment, PaymentStatus.CONFIRMING);
    }

    @Test
    void 업데이트_실패시_예외가_발생한다() {
      // given
      Payment payment = Payment.createPayment(1L, PaymentPhase.INSTANT, "order_123", 50_000L);
      given(paymentRepository.update(payment, PaymentStatus.CONFIRMING)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> paymentDomainService.updateConfirmingPayment(payment))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("주문 ID로 결제 조회 테스트")
  class GetPaymentByOrderIdTest {

    @Test
    void 존재하는_결제를_조회한다() {
      // given
      Payment payment = Payment.createPayment(1L, PaymentPhase.INSTANT, "order_123", 50_000L);
      given(paymentRepository.findByOrderId("order_123")).willReturn(Optional.of(payment));

      // when
      Payment result = paymentDomainService.getPaymentByOrderId("order_123");

      // then
      assertThat(result).isSameAs(payment);
    }

    @Test
    void 존재하지_않는_주문_ID로_조회하면_예외가_발생한다() {
      // given
      given(paymentRepository.findByOrderId("invalid")).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> paymentDomainService.getPaymentByOrderId("invalid"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("참여 ID와 결제 단계로 최신 결제 조회 테스트")
  class FindLatestPaymentTest {

    @Test
    void 결제가_존재하면_반환한다() {
      // given
      Payment payment = Payment.createPayment(1L, PaymentPhase.DEPOSIT, "order_456", 5_000L);
      given(
              paymentRepository.findLatestPaymentByParticipationIdAndPaymentPhase(
                  1L, PaymentPhase.DEPOSIT))
          .willReturn(Optional.of(payment));

      // when
      Optional<Payment> result =
          paymentDomainService.findLatestPaymentByParticipationIdAndPaymentPhase(
              1L, PaymentPhase.DEPOSIT);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isSameAs(payment);
    }

    @Test
    void 결제가_없으면_빈_Optional을_반환한다() {
      // given
      given(
              paymentRepository.findLatestPaymentByParticipationIdAndPaymentPhase(
                  1L, PaymentPhase.DEPOSIT))
          .willReturn(Optional.empty());

      // when
      Optional<Payment> result =
          paymentDomainService.findLatestPaymentByParticipationIdAndPaymentPhase(
              1L, PaymentPhase.DEPOSIT);

      // then
      assertThat(result).isEmpty();
    }
  }
}
