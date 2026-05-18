package buncheoleasy.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Payment 도메인 테스트")
class PaymentTest {

  private static final Long PARTICIPATION_ID = 1L;
  private static final String ORDER_ID = "order_abc123";
  private static final long AMOUNT = 50_000L;

  private Payment createPendingPayment() {
    return Payment.createPayment(PARTICIPATION_ID, ORDER_ID, AMOUNT, Instant.now());
  }

  @Nested
  @DisplayName("결제 생성 테스트")
  class CreatePaymentTest {

    @Test
    void 결제_생성에_성공한다() {
      // when
      Payment payment = Payment.createPayment(PARTICIPATION_ID, ORDER_ID, AMOUNT, Instant.now());

      // then
      assertThat(payment.getParticipationId()).isEqualTo(PARTICIPATION_ID);
      assertThat(payment.getTxType()).isEqualTo(PaymentTxType.PAYMENT);
      assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
      assertThat(payment.getPaymentKey()).isNull();
      assertThat(payment.getParentPaymentId()).isNull();
      assertThat(payment.getAmount()).isEqualTo(AMOUNT);
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
      assertThat(payment.getReason()).isNull();
      assertThat(payment.getRequestedAt()).isNotNull();
      assertThat(payment.getApprovedAt()).isNull();
    }

    @Test
    void participationId가_null이면_예외가_발생한다() {
      assertThatThrownBy(() -> Payment.createPayment(null, ORDER_ID, AMOUNT, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void orderId가_빈_값이면_예외가_발생한다(String invalidOrderId) {
      assertThatThrownBy(
              () -> Payment.createPayment(PARTICIPATION_ID, invalidOrderId, AMOUNT, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void orderId가_null이면_예외가_발생한다() {
      assertThatThrownBy(() -> Payment.createPayment(PARTICIPATION_ID, null, AMOUNT, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -50_000L})
    void 금액이_0_이하면_예외가_발생한다(long invalidAmount) {
      assertThatThrownBy(
              () -> Payment.createPayment(PARTICIPATION_ID, ORDER_ID, invalidAmount, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("결제 승인 시작 테스트")
  class StartConfirmTest {

    @Test
    void PENDING_상태에서_승인_시작에_성공한다() {
      // given
      Payment payment = createPendingPayment();
      String paymentKey = "toss_key_123";

      // when
      payment.startConfirm(paymentKey);

      // then
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
      assertThat(payment.getPaymentKey()).isEqualTo(paymentKey);
    }

    @Test
    void CONFIRMING_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");

      // when & then
      assertThatThrownBy(() -> payment.startConfirm("key2"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void DONE_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");
      payment.completeConfirm(Instant.now());

      // when & then
      assertThatThrownBy(() -> payment.startConfirm("key2"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void FAILED_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();
      payment.fail("실패 사유");

      // when & then
      assertThatThrownBy(() -> payment.startConfirm("key1"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("결제 승인 완료 테스트")
  class CompleteConfirmTest {

    @Test
    void CONFIRMING_상태에서_승인_완료에_성공한다() {
      // given
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");

      // when
      payment.completeConfirm(Instant.now());

      // then
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
      assertThat(payment.getApprovedAt()).isNotNull();
    }

    @Test
    void PENDING_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();

      // when & then
      assertThatThrownBy(() -> payment.completeConfirm(Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void DONE_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");
      payment.completeConfirm(Instant.now());

      // when & then
      assertThatThrownBy(() -> payment.completeConfirm(Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("결제 실패 테스트")
  class FailTest {

    @Test
    void PENDING_상태에서_실패_처리에_성공한다() {
      // given
      Payment payment = createPendingPayment();
      String reason = "사용자 취소";

      // when
      payment.fail(reason);

      // then
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
      assertThat(payment.getReason()).isEqualTo(reason);
    }

    @Test
    void CONFIRMING_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");

      // when & then
      assertThatThrownBy(() -> payment.fail("사유"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void DONE_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");
      payment.completeConfirm(Instant.now());

      // when & then
      assertThatThrownBy(() -> payment.fail("사유"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void FAILED_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();
      payment.fail("최초 사유");

      // when & then
      assertThatThrownBy(() -> payment.fail("두번째 사유"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("승인 중 실패 테스트")
  class FailConfirmTest {

    @Test
    void CONFIRMING_상태에서_승인_실패_처리에_성공한다() {
      // given
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");
      String reason = "토스 승인 거절";

      // when
      payment.failConfirm(reason);

      // then
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
      assertThat(payment.getReason()).isEqualTo(reason);
    }

    @Test
    void PENDING_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();

      // when & then
      assertThatThrownBy(() -> payment.failConfirm("사유"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }

    @Test
    void DONE_상태에서_호출하면_예외가_발생한다() {
      // given
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");
      payment.completeConfirm(Instant.now());

      // when & then
      assertThatThrownBy(() -> payment.failConfirm("사유"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("상태 확인 메서드 테스트")
  class StatusCheckTest {

    @Test
    void PENDING_상태_확인() {
      Payment payment = createPendingPayment();
      assertThat(payment.isPending()).isTrue();
      assertThat(payment.isConfirming()).isFalse();
      assertThat(payment.isDone()).isFalse();
      assertThat(payment.isFailed()).isFalse();
    }

    @Test
    void CONFIRMING_상태_확인() {
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");
      assertThat(payment.isPending()).isFalse();
      assertThat(payment.isConfirming()).isTrue();
      assertThat(payment.isDone()).isFalse();
      assertThat(payment.isFailed()).isFalse();
    }

    @Test
    void DONE_상태_확인() {
      Payment payment = createPendingPayment();
      payment.startConfirm("key1");
      payment.completeConfirm(Instant.now());
      assertThat(payment.isPending()).isFalse();
      assertThat(payment.isConfirming()).isFalse();
      assertThat(payment.isDone()).isTrue();
      assertThat(payment.isFailed()).isFalse();
    }

    @Test
    void FAILED_상태_확인() {
      Payment payment = createPendingPayment();
      payment.fail("사유");
      assertThat(payment.isPending()).isFalse();
      assertThat(payment.isConfirming()).isFalse();
      assertThat(payment.isDone()).isFalse();
      assertThat(payment.isFailed()).isTrue();
    }
  }
}
