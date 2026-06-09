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
  @DisplayName("참여자 소유권 검증 테스트")
  class ValidateOwnedByTest {

    @Test
    void 소유자이면_예외가_발생하지_않는다() {
      Participation participation = newParticipation();

      participation.validateOwnedBy(PARTICIPANT_ID);
    }

    @Test
    void 소유자가_아니면_예외가_발생한다() {
      Participation participation = newParticipation();
      Long otherUserId = 999L;

      assertThatThrownBy(() -> participation.validateOwnedBy(otherUserId))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }
  }

  @Nested
  @DisplayName("참여 취소 테스트")
  class CancelTest {

    @Test
    void ACTIVE_BID_상태에서_취소에_성공한다() {
      Participation participation = newParticipation();
      Instant now = Instant.parse("2026-03-11T17:00:00Z");

      participation.cancel(now);

      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CANCELLED);
      assertThat(participation.getFinalizedAt()).isEqualTo(now);
      assertThat(participation.getFailReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"AWAITING_PAYMENT", "CONFIRMED", "CANCELLED", "FAILED"})
    void ACTIVE_BID이_아닌_상태에서_취소하면_예외가_발생한다(ParticipationStatus invalidStatus) {
      Participation participation = newParticipation();
      setStatus(participation, invalidStatus);

      assertThatThrownBy(() -> participation.cancel(Instant.now()))
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

  @Nested
  @DisplayName("낙찰자 선정 테스트")
  class AwardAsWinnerTest {

    @Test
    void ACTIVE_BID_상태에서_낙찰_선정에_성공한다() {
      Participation participation = newParticipation();
      Instant dueAt = Instant.parse("2026-03-13T15:30:00Z");

      participation.awardAsWinner(dueAt);

      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(participation.getClosedRank()).isEqualTo(1);
      assertThat(participation.getDueAt()).isEqualTo(dueAt);
      assertThat(participation.getFinalizedAt()).isNull();
      assertThat(participation.getFailReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"AWAITING_PAYMENT", "CONFIRMED", "CANCELLED", "FAILED"})
    void ACTIVE_BID이_아닌_상태에서_낙찰_선정하면_예외가_발생한다(ParticipationStatus invalidStatus) {
      Participation participation = newParticipation();
      setStatus(participation, invalidStatus);

      assertThatThrownBy(() -> participation.awardAsWinner(Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("낙찰 실패(미선정) 테스트")
  class MarkNotSelectedTest {

    @Test
    void ACTIVE_BID_상태에서_미선정_처리에_성공한다() {
      Participation participation = newParticipation();
      Instant now = Instant.parse("2026-03-13T16:00:00Z");

      participation.markNotSelected(2, now);

      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.FAILED);
      assertThat(participation.getClosedRank()).isEqualTo(2);
      assertThat(participation.getFailReason()).isEqualTo("낙찰 실패");
      assertThat(participation.getFinalizedAt()).isEqualTo(now);
      assertThat(participation.getDueAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"AWAITING_PAYMENT", "CONFIRMED", "CANCELLED", "FAILED"})
    void ACTIVE_BID이_아닌_상태에서_미선정_처리하면_예외가_발생한다(ParticipationStatus invalidStatus) {
      Participation participation = newParticipation();
      setStatus(participation, invalidStatus);

      assertThatThrownBy(() -> participation.markNotSelected(2, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("미입금 만료 테스트")
  class ExpireUnpaidTest {

    private static final Instant DUE_AT = Instant.parse("2026-03-13T12:00:00Z");
    private static final Instant AFTER_DUE = Instant.parse("2026-03-13T12:00:01Z");

    @Test
    void AWAITING_PAYMENT_이고_기한이_지났으면_FAILED_로_전이한다() {
      Participation participation = awaitingWithDue(DUE_AT);

      participation.expireUnpaid(AFTER_DUE);

      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.FAILED);
      assertThat(participation.getFailReason()).isEqualTo("입금 기한 초과");
      assertThat(participation.getFinalizedAt()).isEqualTo(AFTER_DUE);
    }

    @Test
    void dueAt_이_null_이면_PAYMENT_NOT_DUE_YET_예외가_발생한다() {
      Participation participation = newParticipation();
      setStatus(participation, ParticipationStatus.AWAITING_PAYMENT); // dueAt 은 null

      assertThatThrownBy(() -> participation.expireUnpaid(AFTER_DUE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_PAYMENT_NOT_DUE_YET);
    }

    @Test
    void 기한_정각이거나_아직_안_지났으면_PAYMENT_NOT_DUE_YET_예외가_발생한다() {
      Participation participation = awaitingWithDue(DUE_AT);

      // now == dueAt (정각) 은 아직 만료 아님
      assertThatThrownBy(() -> participation.expireUnpaid(DUE_AT))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_PAYMENT_NOT_DUE_YET);
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"ACTIVE_BID", "PAYMENT_REPORTED", "CONFIRMED", "CANCELLED", "FAILED"})
    void AWAITING_PAYMENT_이_아니면_만료_대상이_아니라_예외가_발생한다(ParticipationStatus invalidStatus) {
      // PAYMENT_REPORTED/CONFIRMED 포함 — 입금신고·확정 건은 만료되지 않는다.
      Participation participation = newParticipation();
      setStatus(participation, invalidStatus);
      setField(participation, "dueAt", DUE_AT);

      assertThatThrownBy(() -> participation.expireUnpaid(AFTER_DUE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }

    private Participation awaitingWithDue(final Instant dueAt) {
      Participation participation = newParticipation();
      setStatus(participation, ParticipationStatus.AWAITING_PAYMENT);
      setField(participation, "dueAt", dueAt);
      return participation;
    }
  }

  @Nested
  @DisplayName("차순위 승계 테스트")
  class PromoteToWinnerTest {

    private static final Instant NEW_DUE_AT = Instant.parse("2026-03-14T12:00:00Z");

    @Test
    void ACTIVE_BID_을_AWAITING_PAYMENT_로_승계하고_closedRank_는_보존한다() {
      Participation participation = newParticipation();
      setField(participation, "closedRank", 2); // 마감 시점 2순위

      participation.promoteToWinner(NEW_DUE_AT);

      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(participation.getDueAt()).isEqualTo(NEW_DUE_AT);
      assertThat(participation.getClosedRank()).isEqualTo(2); // 덮어쓰지 않는다
    }

    @ParameterizedTest
    @EnumSource(
        value = ParticipationStatus.class,
        names = {"AWAITING_PAYMENT", "PAYMENT_REPORTED", "CONFIRMED", "CANCELLED", "FAILED"})
    void ACTIVE_BID_이_아니면_승계할_수_없어_예외가_발생한다(ParticipationStatus invalidStatus) {
      Participation participation = newParticipation();
      setStatus(participation, invalidStatus);

      assertThatThrownBy(() -> participation.promoteToWinner(NEW_DUE_AT))
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
    setField(participation, "status", status);
  }

  private static void setField(
      final Object target, final String fieldName, final Object value) {
    try {
      Field field = Participation.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
