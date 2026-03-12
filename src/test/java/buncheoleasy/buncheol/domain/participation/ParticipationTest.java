package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Participation 도메인 테스트")
class ParticipationTest {

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long BUNCHEOL_MEMBER_ID = 10L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;
  private static final long INSTANT_PRICE = 50_000L;
  private static final long BID_AMOUNT = 30_000L;

  @Nested
  @DisplayName("즉시 구매 참여 생성 테스트")
  class CreateInstantTest {

    @Test
    void 즉시_구매_참여_생성에_성공한다() {
      // when
      Participation participation =
          Participation.createInstant(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, INSTANT_PRICE);

      // then
      assertThat(participation.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(participation.getBuncheolMemberId()).isEqualTo(BUNCHEOL_MEMBER_ID);
      assertThat(participation.getParticipantId()).isEqualTo(PARTICIPANT_ID);
      assertThat(participation.getShippingAddressId()).isEqualTo(SHIPPING_ADDRESS_ID);
      assertThat(participation.getType()).isEqualTo(ParticipationType.INSTANT);
      assertThat(participation.getInstantPriceSnapshot()).isEqualTo(INSTANT_PRICE);
      assertThat(participation.getBidAmount()).isNull();
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.PAYMENT_PENDING);
      assertThat(participation.getBalanceDueAmount()).isNull();
      assertThat(participation.getBalanceDueAt()).isNull();
      assertThat(participation.getClosedRank()).isNull();
      assertThat(participation.getFailReason()).isNull();
      assertThat(participation.getFinalizedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -50_000L})
    void 즉시_구매_가격이_0_이하면_예외가_발생한다(long invalidPrice) {
      // when & then
      assertThatThrownBy(
              () ->
                  Participation.createInstant(
                      BUNCHEOL_ID,
                      BUNCHEOL_MEMBER_ID,
                      PARTICIPANT_ID,
                      SHIPPING_ADDRESS_ID,
                      invalidPrice))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("제시 참여 생성 테스트")
  class CreateBidTest {

    @Test
    void 제시_참여_생성에_성공한다() {
      // when
      Participation participation =
          Participation.createBid(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);

      // then
      assertThat(participation.getType()).isEqualTo(ParticipationType.BID);
      assertThat(participation.getBidAmount()).isEqualTo(BID_AMOUNT);
      assertThat(participation.getInstantPriceSnapshot()).isNull();
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.PAYMENT_PENDING);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -30_000L})
    void 제시_금액이_0_이하면_예외가_발생한다(long invalidBidAmount) {
      // when & then
      assertThatThrownBy(
              () ->
                  Participation.createBid(
                      BUNCHEOL_ID,
                      BUNCHEOL_MEMBER_ID,
                      PARTICIPANT_ID,
                      SHIPPING_ADDRESS_ID,
                      invalidBidAmount))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("예치금 결제 완료 후 제시 활성화 테스트")
  class ActivateBidAfterDepositPaymentTest {

    @Test
    void BID_타입_PAYMENT_PENDING_상태에서_활성화에_성공한다() {
      // given
      Participation participation =
          Participation.createBid(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);

      // when
      participation.activateBidAfterDepositPayment();

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.ACTIVE_BID);
      assertThat(participation.getFailReason()).isNull();
      assertThat(participation.getFinalizedAt()).isNull();
    }

    @Test
    void INSTANT_타입이면_예외가_발생한다() {
      // given
      Participation participation =
          Participation.createInstant(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, INSTANT_PRICE);

      // when & then
      assertThatThrownBy(participation::activateBidAfterDepositPayment)
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }

    @Test
    void ACTIVE_BID_상태에서_호출하면_예외가_발생한다() {
      // given
      Participation participation =
          Participation.createBid(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
      participation.activateBidAfterDepositPayment();

      // when & then
      assertThatThrownBy(participation::activateBidAfterDepositPayment)
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("즉시 구매 확정 테스트")
  class ConfirmInstantParticipationTest {

    @Test
    void INSTANT_타입_PAYMENT_PENDING_상태에서_확정에_성공한다() {
      // given
      Participation participation =
          Participation.createInstant(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, INSTANT_PRICE);
      LocalDateTime now = LocalDateTime.of(2026, 3, 11, 12, 0);

      // when
      participation.confirmInstantParticipation(now);

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(participation.getFinalizedAt()).isEqualTo(now);
      assertThat(participation.getFailReason()).isNull();
    }

    @Test
    void BID_타입이면_예외가_발생한다() {
      // given
      Participation participation =
          Participation.createBid(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
      LocalDateTime now = LocalDateTime.now();

      // when & then
      assertThatThrownBy(() -> participation.confirmInstantParticipation(now))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"ACTIVE_BID", "AWAITING_BALANCE_PAYMENT", "CONFIRMED", "CANCELLED", "FAILED"})
    void PAYMENT_PENDING이_아닌_상태에서_호출하면_예외가_발생한다(ParticipationStatus invalidStatus) {
      // given
      Participation participation =
          Participation.createInstant(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, INSTANT_PRICE);
      setStatus(participation, invalidStatus);

      // when & then
      assertThatThrownBy(() -> participation.confirmInstantParticipation(LocalDateTime.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("잔금 결제 완료 확정 테스트")
  class ConfirmBalancePaymentTest {

    @Test
    void AWAITING_BALANCE_PAYMENT_상태에서_확정에_성공한다() {
      // given
      Participation participation =
          Participation.createBid(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
      setStatus(participation, ParticipationStatus.AWAITING_BALANCE_PAYMENT);
      LocalDateTime now = LocalDateTime.of(2026, 3, 11, 15, 30);

      // when
      participation.confirmBalancePayment(now);

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(participation.getFinalizedAt()).isEqualTo(now);
      assertThat(participation.getFailReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"PAYMENT_PENDING", "ACTIVE_BID", "CONFIRMED", "CANCELLED", "FAILED"})
    void AWAITING_BALANCE_PAYMENT이_아닌_상태에서_호출하면_예외가_발생한다(ParticipationStatus invalidStatus) {
      // given
      Participation participation =
          Participation.createBid(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
      setStatus(participation, invalidStatus);

      // when & then
      assertThatThrownBy(() -> participation.confirmBalancePayment(LocalDateTime.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("참여 실패 테스트")
  class FailTest {

    private static final String FAIL_REASON = "결제 실패";

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"PAYMENT_PENDING", "ACTIVE_BID", "AWAITING_BALANCE_PAYMENT"})
    void 허용된_상태에서_실패_처리에_성공한다(ParticipationStatus allowedStatus) {
      // given
      Participation participation =
          Participation.createBid(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
      setStatus(participation, allowedStatus);
      LocalDateTime now = LocalDateTime.of(2026, 3, 11, 18, 0);

      // when
      participation.fail(FAIL_REASON, now);

      // then
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.FAILED);
      assertThat(participation.getFailReason()).isEqualTo(FAIL_REASON);
      assertThat(participation.getFinalizedAt()).isEqualTo(now);
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"CONFIRMED", "CANCELLED", "FAILED"})
    void 허용되지_않은_상태에서_실패_처리하면_예외가_발생한다(ParticipationStatus invalidStatus) {
      // given
      Participation participation =
          Participation.createBid(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
      setStatus(participation, invalidStatus);

      // when & then
      assertThatThrownBy(() -> participation.fail(FAIL_REASON, LocalDateTime.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  private void setStatus(final Participation participation, final ParticipationStatus status) {
    try {
      Field statusField = Participation.class.getDeclaredField("status");
      statusField.setAccessible(true);
      statusField.set(participation, status);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
