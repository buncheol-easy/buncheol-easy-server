package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.Instant;
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
  private static final long BID_AMOUNT = 30_000L;

  @Nested
  @DisplayName("참여 생성 테스트")
  class CreateTest {

    @Test
    void 참여_생성_시_ACTIVE_BID_상태로_시작한다() {
      Participation participation =
          Participation.create(
              BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);

      assertThat(participation.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(participation.getBuncheolMemberId()).isEqualTo(BUNCHEOL_MEMBER_ID);
      assertThat(participation.getParticipantId()).isEqualTo(PARTICIPANT_ID);
      assertThat(participation.getShippingAddressId()).isEqualTo(SHIPPING_ADDRESS_ID);
      assertThat(participation.getBidAmount()).isEqualTo(BID_AMOUNT);
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.ACTIVE_BID);
      assertThat(participation.getDueAt()).isNull();
      assertThat(participation.getClosedRank()).isNull();
      assertThat(participation.getFailReason()).isNull();
      assertThat(participation.getFinalizedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -30_000L})
    void 제시_금액이_0_이하면_예외가_발생한다(long invalidBidAmount) {
      assertThatThrownBy(
              () ->
                  Participation.create(
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
  @DisplayName("낙찰자 결제 완료 확정 테스트")
  class ConfirmBalancePaymentTest {

    @Test
    void AWAITING_PAYMENT_상태에서_확정에_성공한다() {
      Participation participation = newParticipation();
      setStatus(participation, ParticipationStatus.AWAITING_PAYMENT);
      Instant now = Instant.parse("2026-03-11T15:30:00Z");

      participation.completePayment(now);

      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(participation.getFinalizedAt()).isEqualTo(now);
      assertThat(participation.getFailReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"ACTIVE_BID", "CONFIRMED", "CANCELLED", "FAILED"})
    void AWAITING_PAYMENT이_아닌_상태에서_호출하면_예외가_발생한다(ParticipationStatus invalidStatus) {
      Participation participation = newParticipation();
      setStatus(participation, invalidStatus);

      assertThatThrownBy(() -> participation.completePayment(Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("참여 실패 테스트")
  class FailTest {

    private static final String FAIL_REASON = "낙찰 실패";

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"ACTIVE_BID", "AWAITING_PAYMENT"})
    void 허용된_상태에서_실패_처리에_성공한다(ParticipationStatus allowedStatus) {
      Participation participation = newParticipation();
      setStatus(participation, allowedStatus);
      Instant now = Instant.parse("2026-03-11T18:00:00Z");

      participation.fail(FAIL_REASON, now);

      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.FAILED);
      assertThat(participation.getFailReason()).isEqualTo(FAIL_REASON);
      assertThat(participation.getFinalizedAt()).isEqualTo(now);
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"CONFIRMED", "CANCELLED", "FAILED"})
    void 허용되지_않은_상태에서_실패_처리하면_예외가_발생한다(ParticipationStatus invalidStatus) {
      Participation participation = newParticipation();
      setStatus(participation, invalidStatus);

      assertThatThrownBy(() -> participation.fail(FAIL_REASON, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  private static Participation newParticipation() {
    return Participation.create(
        BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
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
