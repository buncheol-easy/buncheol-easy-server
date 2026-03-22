package buncheoleasy.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.domain.Payment;
import buncheoleasy.payment.domain.PaymentDomainService;
import buncheoleasy.payment.domain.PaymentPhase;
import buncheoleasy.payment.infrastructure.TossPaymentClient;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmRejectedException;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmResponse;
import buncheoleasy.payment.infrastructure.TossPaymentClient.TossConfirmUnavailableException;
import buncheoleasy.payment.infrastructure.TossPaymentsProperties;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

  @InjectMocks private PaymentService paymentService;

  @Mock private PaymentDomainService paymentDomainService;
  @Mock private PaymentCompletionHandler paymentCompletionHandler;
  @Mock private TossPaymentClient tossPaymentClient;
  @Mock private TossPaymentsProperties tossPaymentsProperties;
  @Mock private TransactionTemplate transactionTemplate;

  private static final Long PARTICIPATION_ID = 1L;
  private static final String ORDER_ID = "order_abc123";
  private static final String PAYMENT_KEY = "toss_key_456";
  private static final long AMOUNT = 50_000L;

  @Nested
  @DisplayName("결제 주문 생성 테스트")
  class CreatePaymentOrderTest {

    @Test
    void 기존_결제가_없으면_새_결제를_생성한다() {
      // given
      given(
              paymentDomainService.findLatestPaymentByParticipationIdAndPaymentPhase(
                  PARTICIPATION_ID, PaymentPhase.INSTANT))
          .willReturn(Optional.empty());
      given(paymentDomainService.create(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
      given(tossPaymentsProperties.clientKey()).willReturn("test_client_key");
      setBaseUrl("http://localhost:8080");

      // when
      PaymentOrderInfo result =
          paymentService.createPaymentOrder(
              PARTICIPATION_ID, PaymentPhase.INSTANT, AMOUNT, "테스트 결제");

      // then
      assertThat(result.clientKey()).isEqualTo("test_client_key");
      assertThat(result.paymentOrderName()).isEqualTo("테스트 결제");
      assertThat(result.amount()).isEqualTo(AMOUNT);
      assertThat(result.paymentOrderId()).startsWith("order_");
      then(paymentDomainService).should().create(any(Payment.class));
    }

    @Test
    void 동일_금액의_PENDING_결제가_있으면_재사용한다() {
      // given
      Payment existingPayment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);

      given(
              paymentDomainService.findLatestPaymentByParticipationIdAndPaymentPhase(
                  PARTICIPATION_ID, PaymentPhase.INSTANT))
          .willReturn(Optional.of(existingPayment));
      given(tossPaymentsProperties.clientKey()).willReturn("test_client_key");
      setBaseUrl("http://localhost:8080");

      // when
      PaymentOrderInfo result =
          paymentService.createPaymentOrder(
              PARTICIPATION_ID, PaymentPhase.INSTANT, AMOUNT, "테스트 결제");

      // then
      assertThat(result.paymentOrderId()).isEqualTo(ORDER_ID);
      then(paymentDomainService).should(never()).create(any());
    }

    @Test
    void 다른_금액의_PENDING_결제가_있으면_실패_처리_후_새로_생성한다() {
      // given
      Payment existingPayment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, 30_000L);

      given(
              paymentDomainService.findLatestPaymentByParticipationIdAndPaymentPhase(
                  PARTICIPATION_ID, PaymentPhase.INSTANT))
          .willReturn(Optional.of(existingPayment));
      given(paymentDomainService.create(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
      given(tossPaymentsProperties.clientKey()).willReturn("test_client_key");
      setBaseUrl("http://localhost:8080");

      // when
      PaymentOrderInfo result =
          paymentService.createPaymentOrder(
              PARTICIPATION_ID, PaymentPhase.INSTANT, AMOUNT, "테스트 결제");

      // then
      assertThat(result.amount()).isEqualTo(AMOUNT);
      assertThat(result.paymentOrderId()).isNotEqualTo(ORDER_ID);
      then(paymentDomainService).should().updatePendingPayment(existingPayment);
      then(paymentDomainService).should().create(any(Payment.class));
    }

    @Test
    void CONFIRMING_상태의_결제가_있으면_예외가_발생한다() {
      // given
      Payment existingPayment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      existingPayment.startConfirm(PAYMENT_KEY);

      given(
              paymentDomainService.findLatestPaymentByParticipationIdAndPaymentPhase(
                  PARTICIPATION_ID, PaymentPhase.INSTANT))
          .willReturn(Optional.of(existingPayment));

      // when & then
      assertThatThrownBy(
              () ->
                  paymentService.createPaymentOrder(
                      PARTICIPATION_ID, PaymentPhase.INSTANT, AMOUNT, "테스트 결제"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void DONE_상태의_결제가_있으면_예외가_발생한다() {
      // given
      Payment existingPayment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      existingPayment.startConfirm(PAYMENT_KEY);
      existingPayment.completeConfirm();

      given(
              paymentDomainService.findLatestPaymentByParticipationIdAndPaymentPhase(
                  PARTICIPATION_ID, PaymentPhase.INSTANT))
          .willReturn(Optional.of(existingPayment));

      // when & then
      assertThatThrownBy(
              () ->
                  paymentService.createPaymentOrder(
                      PARTICIPATION_ID, PaymentPhase.INSTANT, AMOUNT, "테스트 결제"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_ALREADY_APPROVED);
    }

    @Test
    void FAILED_상태의_결제가_있으면_새로_생성한다() {
      // given
      Payment existingPayment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      existingPayment.fail("이전 실패");

      given(
              paymentDomainService.findLatestPaymentByParticipationIdAndPaymentPhase(
                  PARTICIPATION_ID, PaymentPhase.INSTANT))
          .willReturn(Optional.of(existingPayment));
      given(paymentDomainService.create(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
      given(tossPaymentsProperties.clientKey()).willReturn("test_client_key");
      setBaseUrl("http://localhost:8080");

      // when
      PaymentOrderInfo result =
          paymentService.createPaymentOrder(
              PARTICIPATION_ID, PaymentPhase.INSTANT, AMOUNT, "테스트 결제");

      // then
      assertThat(result.paymentOrderId()).isNotEqualTo(ORDER_ID);
      then(paymentDomainService).should().create(any(Payment.class));
    }
  }

  @Nested
  @DisplayName("결제 취소 테스트")
  class CancelPendingPaymentTest {

    @Test
    void PENDING_상태_결제_취소에_성공한다() {
      // given
      Long userId = 100L;
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);
      willDoNothing().given(paymentCompletionHandler).validateOwnership(PARTICIPATION_ID, userId);

      // when
      paymentService.cancelPendingPayment(userId, ORDER_ID, "PAY_CANCEL", "사용자 취소");

      // then
      assertThat(payment.isFailed()).isTrue();
      then(paymentDomainService).should().updatePendingPayment(payment);
      then(paymentCompletionHandler)
          .should()
          .onPaymentFailed(eq(PARTICIPATION_ID), eq(PaymentPhase.INSTANT), anyString());
    }

    @Test
    void 이미_실패한_결제이면_아무것도_하지_않는다() {
      // given
      Long userId = 100L;
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      payment.fail("이미 실패");

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);
      willDoNothing().given(paymentCompletionHandler).validateOwnership(PARTICIPATION_ID, userId);

      // when
      paymentService.cancelPendingPayment(userId, ORDER_ID, "CODE", "message");

      // then
      then(paymentDomainService).should(never()).updatePendingPayment(any());
      then(paymentCompletionHandler).should(never()).onPaymentFailed(any(), any(), any());
    }

    @Test
    void 이미_승인된_결제이면_예외가_발생한다() {
      // given
      Long userId = 100L;
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      payment.startConfirm(PAYMENT_KEY);
      payment.completeConfirm();

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);
      willDoNothing().given(paymentCompletionHandler).validateOwnership(PARTICIPATION_ID, userId);

      // when & then
      assertThatThrownBy(
              () -> paymentService.cancelPendingPayment(userId, ORDER_ID, "CODE", "message"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_ALREADY_APPROVED);
    }

    @Test
    void CONFIRMING_상태이면_예외가_발생한다() {
      // given
      Long userId = 100L;
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      payment.startConfirm(PAYMENT_KEY);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);
      willDoNothing().given(paymentCompletionHandler).validateOwnership(PARTICIPATION_ID, userId);

      // when & then
      assertThatThrownBy(
              () -> paymentService.cancelPendingPayment(userId, ORDER_ID, "CODE", "message"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void 소유권_검증_실패시_예외가_발생한다() {
      // given
      Long wrongUserId = 999L;
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);
      willThrow(new BusinessException(ErrorCode.PAYMENT_NO_PERMISSION))
          .given(paymentCompletionHandler)
          .validateOwnership(PARTICIPATION_ID, wrongUserId);

      // when & then
      assertThatThrownBy(
              () -> paymentService.cancelPendingPayment(wrongUserId, ORDER_ID, "CODE", "message"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_NO_PERMISSION);
    }
  }

  @Nested
  @DisplayName("결제 승인 테스트")
  class ConfirmPaymentTest {

    private void stubTransactionTemplate() {
      willAnswer(
              inv -> {
                Consumer<TransactionStatus> action = inv.getArgument(0);
                action.accept(null);
                return null;
              })
          .given(transactionTemplate)
          .executeWithoutResult(any());
    }

    @Test
    void PENDING_결제_승인에_성공한다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);

      TossConfirmResponse tossResponse =
          new TossConfirmResponse(AMOUNT, "카드", "2026-03-11T12:00:00");
      given(tossPaymentClient.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT)).willReturn(tossResponse);

      stubTransactionTemplate();

      // when
      paymentService.confirmPayment(PAYMENT_KEY, ORDER_ID, AMOUNT);

      // then
      assertThat(payment.isDone()).isTrue();
      then(paymentDomainService).should().updatePendingPayment(payment);
      then(paymentDomainService).should().updateConfirmingPayment(payment);
      then(paymentCompletionHandler)
          .should()
          .onPaymentCompleted(PARTICIPATION_ID, PaymentPhase.INSTANT);
    }

    @Test
    void 토스_거절_예외_발생시_결제가_실패_처리된다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);
      given(tossPaymentClient.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT))
          .willThrow(new TossConfirmRejectedException("REJECT_CODE", "잔액 부족"));

      stubTransactionTemplate();

      // when & then
      assertThatThrownBy(() -> paymentService.confirmPayment(PAYMENT_KEY, ORDER_ID, AMOUNT))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_TOSS_CONFIRM_REJECTED);

      assertThat(payment.isFailed()).isTrue();
      then(paymentCompletionHandler)
          .should()
          .onPaymentFailed(eq(PARTICIPATION_ID), eq(PaymentPhase.INSTANT), anyString());
    }

    @Test
    void 토스_네트워크_장애시_CONFIRMING_상태를_유지한다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);
      given(tossPaymentClient.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT))
          .willThrow(new TossConfirmUnavailableException("NETWORK_ERROR", "네트워크 오류"));

      // when & then
      assertThatThrownBy(() -> paymentService.confirmPayment(PAYMENT_KEY, ORDER_ID, AMOUNT))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_TOSS_CONFIRM_UNAVAILABLE);

      // CONFIRMING 유지, fail 처리하지 않음
      assertThat(payment.isConfirming()).isTrue();
      then(paymentCompletionHandler)
          .should(never())
          .onPaymentFailed(any(Long.class), any(PaymentPhase.class), anyString());
    }

    @Test
    void 토스_응답_금액_불일치시_예외가_발생한다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);

      TossConfirmResponse tossResponse =
          new TossConfirmResponse(99_999L, "카드", "2026-03-11T12:00:00");
      given(tossPaymentClient.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT)).willReturn(tossResponse);

      // when & then
      assertThatThrownBy(() -> paymentService.confirmPayment(PAYMENT_KEY, ORDER_ID, AMOUNT))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    void 이미_승인된_결제를_같은_키로_재요청하면_멱등_처리된다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      payment.startConfirm(PAYMENT_KEY);
      payment.completeConfirm();

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);

      // when
      paymentService.confirmPayment(PAYMENT_KEY, ORDER_ID, AMOUNT);

      // then (토스 호출 없이 정상 반환)
      then(tossPaymentClient).should(never()).confirm(anyString(), anyString(), anyLong());
    }

    @Test
    void 이미_승인된_결제를_다른_키로_재요청하면_예외가_발생한다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      payment.startConfirm(PAYMENT_KEY);
      payment.completeConfirm();

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);

      // when & then
      assertThatThrownBy(() -> paymentService.confirmPayment("different_key", ORDER_ID, AMOUNT))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_ALREADY_APPROVED);
    }

    @Test
    void 준비_단계에서_금액_불일치시_예외가_발생한다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);

      // when & then
      assertThatThrownBy(() -> paymentService.confirmPayment(PAYMENT_KEY, ORDER_ID, 99_999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    void CONFIRMING_상태에서_같은_키로_재시도하면_토스_승인을_진행한다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      payment.startConfirm(PAYMENT_KEY);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);

      TossConfirmResponse tossResponse =
          new TossConfirmResponse(AMOUNT, "카드", "2026-03-11T12:00:00");
      given(tossPaymentClient.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT)).willReturn(tossResponse);

      stubTransactionTemplate();

      // when
      paymentService.confirmPayment(PAYMENT_KEY, ORDER_ID, AMOUNT);

      // then (토스 승인 호출)
      then(tossPaymentClient).should().confirm(PAYMENT_KEY, ORDER_ID, AMOUNT);
    }

    @Test
    void CONFIRMING_상태에서_다른_키로_재시도하면_예외가_발생한다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      payment.startConfirm(PAYMENT_KEY);

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);

      // when & then
      assertThatThrownBy(() -> paymentService.confirmPayment("different_key", ORDER_ID, AMOUNT))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void FAILED_상태의_결제_승인_요청시_예외가_발생한다() {
      // given
      Payment payment =
          Payment.createPayment(PARTICIPATION_ID, PaymentPhase.INSTANT, ORDER_ID, AMOUNT);
      payment.fail("이전 실패");

      given(paymentDomainService.getPaymentByOrderId(ORDER_ID)).willReturn(payment);

      // when & then
      assertThatThrownBy(() -> paymentService.confirmPayment(PAYMENT_KEY, ORDER_ID, AMOUNT))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  private void setBaseUrl(String url) {
    try {
      Field field = PaymentService.class.getDeclaredField("baseUrl");
      field.setAccessible(true);
      field.set(paymentService, url);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
