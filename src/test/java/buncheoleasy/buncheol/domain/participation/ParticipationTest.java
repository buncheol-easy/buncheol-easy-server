package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Participation 도메인 테스트")
class ParticipationTest {

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long BUNCHEOL_MEMBER_ID = 10L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;
  private static final long AMOUNT = 31_900L;
  private static final long SHIPPING_FEE = 3_000L;
  private static final RefundAccount REFUND_ACCOUNT = RefundAccount.of("국민", "12345678", "홍길동");
  private static final Instant DUE_AT = Instant.parse("2026-03-11T15:30:00Z");

  @Nested
  @DisplayName("참여 생성 테스트")
  class CreateTest {

    @Test
    void 참여_생성_시_입금확인중_상태로_시작하고_종료_필드는_비어있다() {
      Participation participation = newParticipation();

      assertThat(participation.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(participation.getBuncheolMemberId()).isEqualTo(BUNCHEOL_MEMBER_ID);
      assertThat(participation.getParticipantId()).isEqualTo(PARTICIPANT_ID);
      assertThat(participation.getShippingAddressId()).isEqualTo(SHIPPING_ADDRESS_ID);
      assertThat(participation.getAmount()).isEqualTo(AMOUNT);
      assertThat(participation.getShippingFee()).isEqualTo(SHIPPING_FEE);
      assertThat(participation.getTotalAmount()).isEqualTo(AMOUNT + SHIPPING_FEE);
      assertThat(participation.getRefundAccount()).isEqualTo(REFUND_ACCOUNT);
      assertThat(participation.getDueAt()).isEqualTo(DUE_AT);
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(participation.getConfirmedAt()).isNull();
      assertThat(participation.getCancelledAt()).isNull();
      assertThat(participation.getCancelReason()).isNull();
    }

    @Test
    void 환불계좌가_null_이면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  Participation.create(
                      BUNCHEOL_ID,
                      BUNCHEOL_MEMBER_ID,
                      PARTICIPANT_ID,
                      SHIPPING_ADDRESS_ID,
                      AMOUNT,
                      SHIPPING_FEE,
                      null,
                      DUE_AT))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 입금_만료시각이_null_이면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  Participation.create(
                      BUNCHEOL_ID,
                      BUNCHEOL_MEMBER_ID,
                      PARTICIPANT_ID,
                      SHIPPING_ADDRESS_ID,
                      AMOUNT,
                      SHIPPING_FEE,
                      REFUND_ACCOUNT,
                      null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_REQUIRED_FIELD_MISSING);
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
  @DisplayName("배송비 환급 전이 테스트")
  class PaybackTransitionTest {

    private static final PaybackTweetUrl TWEET_URL =
        PaybackTweetUrl.parse("https://x.com/fan/status/123");
    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    @Test
    void 참여_생성_시_환급_상태는_NONE_이고_환급_필드는_비어있다() {
      Participation participation = newParticipation();

      assertThat(participation.getPaybackStatus()).isEqualTo(PaybackStatus.NONE);
      assertThat(participation.getPaybackTweetUrl()).isNull();
      assertThat(participation.getPaybackRequestedAt()).isNull();
      assertThat(participation.getPaybackCompletedAt()).isNull();
      assertThat(participation.getPaybackRejectReason()).isNull();
      assertThat(participation.getPaybackAmount()).isNull();
    }

    @Test
    void 신청하면_REQUESTED_로_전이하고_배송비를_환급액으로_스냅샷한다() {
      Participation participation = newParticipation();

      participation.requestPayback(TWEET_URL, NOW);

      assertThat(participation.getPaybackStatus()).isEqualTo(PaybackStatus.REQUESTED);
      assertThat(participation.getPaybackTweetUrl()).isEqualTo(TWEET_URL.value());
      assertThat(participation.getPaybackRequestedAt()).isEqualTo(NOW);
      assertThat(participation.getPaybackAmount()).isEqualTo(SHIPPING_FEE);
    }

    @Test
    void 확인중_상태에서_다시_제출하면_트윗_링크가_수정된다() {
      Participation participation = newParticipation();
      participation.requestPayback(TWEET_URL, NOW);

      PaybackTweetUrl fixedUrl = PaybackTweetUrl.parse("https://x.com/fan/status/789");
      Instant editedAt = NOW.plusSeconds(600);
      participation.requestPayback(fixedUrl, editedAt);

      assertThat(participation.getPaybackStatus()).isEqualTo(PaybackStatus.REQUESTED);
      assertThat(participation.getPaybackTweetUrl()).isEqualTo(fixedUrl.value());
      assertThat(participation.getPaybackRequestedAt()).isEqualTo(editedAt);
    }

    // 운영진 완료/반려 전이는 CAS(completePaybackIfRequested/rejectPaybackIfRequested)로만 하므로
    // 엔티티 전이 테스트가 없다 — 전이·상태 가드는 JpaParticipationRepositoryAdapterTest 가 검증한다.
  }

  private static Participation newParticipation() {
    return Participation.create(
        BUNCHEOL_ID,
        BUNCHEOL_MEMBER_ID,
        PARTICIPANT_ID,
        SHIPPING_ADDRESS_ID,
        AMOUNT,
        SHIPPING_FEE,
        REFUND_ACCOUNT,
        DUE_AT);
  }
}
