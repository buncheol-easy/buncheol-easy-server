package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Participation 도메인 테스트")
class ParticipationTest {

  private final ShippingFeeAttribution fees = ShippingFeeAttribution.empty();

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
      assertThat(participation.getAmount() + participation.getShippingFee())
          .isEqualTo(AMOUNT + SHIPPING_FEE);
      assertThat(participation.getDueAt()).isEqualTo(DUE_AT);
      assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(participation.getConfirmedAt()).isNull();
      assertThat(participation.getCancelledAt()).isNull();
      assertThat(participation.getCancelReason()).isNull();
    }

    // 계좌 불변식은 묶음으로 옮겨갔다 (P2-c) — participation_bundles.refund_* 가 NOT NULL 이고
    // ParticipationBundle.open() 이 null 을 거부한다. 참여는 이제 계좌를 갖지 않는다.

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

      participation.requestPayback(TWEET_URL, NOW, fees);

      assertThat(participation.getPaybackStatus()).isEqualTo(PaybackStatus.REQUESTED);
      assertThat(participation.getPaybackTweetUrl()).isEqualTo(TWEET_URL.value());
      assertThat(participation.getPaybackRequestedAt()).isEqualTo(NOW);
      assertThat(participation.getPaybackAmount()).isEqualTo(SHIPPING_FEE);
    }

    // 첫 스냅샷 보존 가드를 고정한다.
    //
    // ⚠️ <b>지금은 도달 불가다.</b> 환급은 LEGACY 전용이고 LEGACY 는 묶음당 슬롯이 1개라, 그 슬롯이
    // 활성이든 취소든 언제나 carrier 여서 귀속값이 0 이 될 수 없다. 아래 헬퍼가 <b>LEGACY 에 존재할 수 없는
    // 다슬롯 묶음</b>을 인위로 만들어야 재현된다 — 즉 이건 「지금 일어나는 일」이 아니라
    // <b>환급이 다슬롯(C2C)으로 열리면 유효해지는 방어</b>다.
    //
    // 그때 무엇을 막는가: deriveStatus 는 저장 상태가 NONE 이 아니면 isEventTarget 을 건너뛴다 — 재신청은
    // 「배송비 > 0」 재검증을 받지 않는다. 그 경로로 0 이 내려오면 확정된 환급액이 0 으로 덮여 어드민 검수
    // 화면과 알림톡 「환급금액」이 0원으로 나간다.
    @Test
    void 재신청에서_배송비가_0_으로_내려와도_첫_환급액_스냅샷을_지킨다() {
      Participation participation = newParticipation();
      participation.requestPayback(TWEET_URL, NOW, fees);
      assertThat(participation.getPaybackAmount()).isEqualTo(SHIPPING_FEE);

      // 형제 슬롯이 취소되는 등으로 이 슬롯이 carrier 가 아니게 된 상태 = 귀속값 0
      participation.requestPayback(TWEET_URL, NOW.plusSeconds(600), giveCarrierToSibling(participation));

      assertThat(participation.getPaybackAmount()).isEqualTo(SHIPPING_FEE);
    }

    // 반대편 — 귀속값이 있으면 최신 값으로 덮어쓴다(가드가 무조건 보존이 아님을 고정).
    @Test
    void 재신청에서_배송비가_있으면_최신_귀속값으로_덮는다() {
      Participation participation = newParticipation();
      participation.requestPayback(TWEET_URL, NOW, giveCarrierToSibling(participation));
      assertThat(participation.getPaybackAmount()).isZero();

      participation.requestPayback(TWEET_URL, NOW.plusSeconds(600), fees);

      assertThat(participation.getPaybackAmount()).isEqualTo(SHIPPING_FEE);
    }

    /**
     * 형제 슬롯에게 carrier 를 넘겨 이 참여의 귀속 배송비를 0 으로 만든다.
     *
     * <p>{@code empty()} 로는 만들 수 없다. 그건 저장값(SHIPPING_FEE)을 그대로 돌려주는 폴백이라
     * 「귀속이 0 인 상태」와 구분되지 않는다. 묶음은 있고 <b>형제 슬롯이 carrier 를 가져간</b> 상태로 만든다.
     *
     * <p>⚠️ <b>인자를 변형한다</b> — 넘긴 참여에 id·bundleId·status·createdAt 을 심는다. 그러지 않으면
     * 같은 묶음의 슬롯으로 인식되지 않아 carrier 판정 자체가 성립하지 않는다.
     */
    private ShippingFeeAttribution giveCarrierToSibling(final Participation participation) {
      setField(participation, "id", 999L);
      setField(participation, "bundleId", BUNDLE_ID);
      setField(participation, "status", ParticipationStatus.AWAITING_PAYMENT);
      setField(participation, "createdAt", NOW);

      ParticipationBundle bundle = newInstance(ParticipationBundle.class);
      setField(bundle, "id", BUNDLE_ID);
      setField(bundle, "shippingFee", SHIPPING_FEE);

      Participation carrier = newInstance(Participation.class);
      setField(carrier, "id", 998L);
      setField(carrier, "bundleId", BUNDLE_ID);
      setField(carrier, "status", ParticipationStatus.AWAITING_PAYMENT);
      setField(carrier, "createdAt", NOW.minusSeconds(60));

      return ShippingFeeAttribution.ofBundle(bundle, List.of(carrier, participation));
    }

    @Test
    void 확인중_상태에서_다시_제출하면_트윗_링크가_수정된다() {
      Participation participation = newParticipation();
      participation.requestPayback(TWEET_URL, NOW, fees);

      PaybackTweetUrl fixedUrl = PaybackTweetUrl.parse("https://x.com/fan/status/789");
      Instant editedAt = NOW.plusSeconds(600);
      participation.requestPayback(fixedUrl, editedAt, fees);

      assertThat(participation.getPaybackStatus()).isEqualTo(PaybackStatus.REQUESTED);
      assertThat(participation.getPaybackTweetUrl()).isEqualTo(fixedUrl.value());
      assertThat(participation.getPaybackRequestedAt()).isEqualTo(editedAt);
    }

    // 운영진 완료/반려 전이는 CAS(completePaybackIfRequested/rejectPaybackIfRequested)로만 하므로
    // 엔티티 전이 테스트가 없다 — 전이·상태 가드는 JpaParticipationRepositoryAdapterTest 가 검증한다.
  }

  private static final Long BUNDLE_ID = 900L;

  private static <T> T newInstance(final Class<T> type) {
    try {
      Constructor<T> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Participation newParticipation() {
    return Participation.create(
        BUNCHEOL_ID,
        BUNCHEOL_MEMBER_ID,
        PARTICIPANT_ID,
        SHIPPING_ADDRESS_ID,
        AMOUNT,
        SHIPPING_FEE,
        DUE_AT);
  }
}
