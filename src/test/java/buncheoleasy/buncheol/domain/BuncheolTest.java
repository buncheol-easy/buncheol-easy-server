package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Buncheol 도메인 테스트")
class BuncheolTest {

  private static final Long HOST_ID = 1L;
  private static final int MIN_HEADCOUNT = 5;
  private static final Instant FUTURE_DEADLINE = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

  private BuncheolParams validParams() {
    return new BuncheolParams(
        1L, "테스트 분철 제목", "분철 설명입니다.", "공식 스토어", FUTURE_DEADLINE, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);
  }

  @Nested
  @DisplayName("오픈채팅 링크 수정 테스트")
  class UpdateOpenChatUrlTest {

    private Buncheol withOpenChatUrl(String url) {
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      buncheol.updateOpenChatUrl(url);
      return buncheol;
    }

    @Test
    void null_은_기존_값을_유지한다() {
      Buncheol buncheol = withOpenChatUrl("https://open.kakao.com/o/gAbCdEf");

      buncheol.updateOpenChatUrl(null);

      assertThat(buncheol.getOpenChatUrl()).isEqualTo("https://open.kakao.com/o/gAbCdEf");
    }

    @Test
    void 빈_문자열은_링크를_제거한다() {
      Buncheol buncheol = withOpenChatUrl("https://open.kakao.com/o/gAbCdEf");

      buncheol.updateOpenChatUrl("");

      assertThat(buncheol.getOpenChatUrl()).isNull();
    }

    @Test
    void 유효한_값은_검증_후_교체된다() {
      Buncheol buncheol = withOpenChatUrl("https://open.kakao.com/o/gAbCdEf");

      buncheol.updateOpenChatUrl("https://open.kakao.com/o/newLink");

      assertThat(buncheol.getOpenChatUrl()).isEqualTo("https://open.kakao.com/o/newLink");
    }

    @Test
    void 형식이_틀리면_예외가_발생하고_기존_값이_유지된다() {
      Buncheol buncheol = withOpenChatUrl("https://open.kakao.com/o/gAbCdEf");

      assertThatThrownBy(() -> buncheol.updateOpenChatUrl("https://evil.example.com/x"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_OPEN_CHAT_URL_INVALID);
      assertThat(buncheol.getOpenChatUrl()).isEqualTo("https://open.kakao.com/o/gAbCdEf");
    }
  }

  @Nested
  @DisplayName("Buncheol 생성 테스트")
  class CreateTest {

    @Test
    void 유효한_파라미터로_분철_생성에_성공한다() {
      // when
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // then
      assertThat(buncheol.getHostId()).isEqualTo(HOST_ID);
      assertThat(buncheol.getGroupId()).isEqualTo(1L);
      assertThat(buncheol.getTitle()).isEqualTo("테스트 분철 제목");
      assertThat(buncheol.getMinHeadcount()).isEqualTo(MIN_HEADCOUNT);
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(buncheol.getFinalizedAt()).isNull();
    }

    @Test
    void hostId가_null이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> Buncheol.create(null, validParams(), Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void params가_null이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, null, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void groupId가_null이면_예외가_발생한다() {
      // given
      BuncheolParams params =
          new BuncheolParams(null, "제목", null, "스토어명", FUTURE_DEADLINE, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  @Nested
  @DisplayName("제목 검증 테스트")
  class ValidateTitleTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 제목이_null이거나_빈_값이면_예외가_발생한다(String title) {
      // given
      BuncheolParams params =
          new BuncheolParams(1L, title, null, "스토어명", FUTURE_DEADLINE, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 제목이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longTitle = "가".repeat(65);
      BuncheolParams params =
          new BuncheolParams(
              1L, longTitle, null, "스토어명", FUTURE_DEADLINE, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  @Nested
  @DisplayName("설명 검증 테스트")
  class ValidateDescriptionTest {

    @Test
    void 설명이_null이어도_생성에_성공한다() {
      // given
      BuncheolParams params =
          new BuncheolParams(1L, "제목", null, "스토어명", FUTURE_DEADLINE, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatCode(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .doesNotThrowAnyException();
    }

    @Test
    void 설명이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longDescription = "가".repeat(701);
      BuncheolParams params =
          new BuncheolParams(
              1L, "제목", longDescription, "스토어명", FUTURE_DEADLINE, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  @Nested
  @DisplayName("마감일 검증 테스트")
  class ValidateDeadlineTest {

    @Test
    void 마감일이_null이면_예외가_발생한다() {
      // given
      BuncheolParams params =
          new BuncheolParams(1L, "제목", null, "스토어명", null, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_DEADLINE_INVALID);
    }

    @Test
    void 마감일이_현재보다_이전이면_예외가_발생한다() {
      // given
      Instant pastDeadline = Instant.now().minus(1, ChronoUnit.DAYS);
      BuncheolParams params =
          new BuncheolParams(1L, "제목", null, "스토어명", pastDeadline, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_DEADLINE_INVALID);
    }

    @Test
    void 마감일이_현재보다_미래이고_정각이면_유효하다() {
      // given
      BuncheolParams params =
          new BuncheolParams(1L, "제목", null, "스토어명", FUTURE_DEADLINE, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatCode(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .doesNotThrowAnyException();
    }

    @Test
    void 마감일이_정각이_아니면_예외가_발생한다() {
      // given - 미래이지만 분·초가 0이 아닌 마감(정각 아님)
      Instant notOnTheHour =
          Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS).plusSeconds(1);
      BuncheolParams params =
          new BuncheolParams(1L, "제목", null, "스토어명", notOnTheHour, MIN_HEADCOUNT, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_DEADLINE_NOT_ON_THE_HOUR);
    }
  }

  @Nested
  @DisplayName("최소 인원 검증 테스트")
  class ValidateMinHeadcountTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void 최소_인원이_1_미만이면_예외가_발생한다(int minHeadcount) {
      // given
      BuncheolParams params =
          new BuncheolParams(1L, "제목", null, "스토어명", FUTURE_DEADLINE, minHeadcount, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_INVALID);
    }

    @Test
    void 최소_인원이_1_이상이면_유효하다() {
      // given
      BuncheolParams params =
          new BuncheolParams(1L, "제목", null, "스토어명", FUTURE_DEADLINE, 1, 3000, null, FlowType.LEGACY, null);

      // when & then
      assertThatCode(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("초기 상태 테스트")
  class InitialStatusTest {

    @Test
    void 분철_생성_직후_상태는_RECRUITING이다() {
      // when
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // then
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }
  }

  @Nested
  @DisplayName("소유자 검증 테스트")
  class ValidateOwnerTest {

    @Test
    void 개최자가_요청하면_예외가_발생하지_않는다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when & then
      assertThatCode(() -> buncheol.validateOwner(HOST_ID)).doesNotThrowAnyException();
    }

    @Test
    void 개최자가_아니면_예외가_발생한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when & then
      assertThatThrownBy(() -> buncheol.validateOwner(999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);
    }
  }

  @Nested
  @DisplayName("개최자 여부 테스트")
  class IsHostTest {

    @Test
    void 개최자이면_true를_반환한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when & then
      assertThat(buncheol.isHost(HOST_ID)).isTrue();
    }

    @Test
    void 개최자가_아니면_false를_반환한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when & then
      assertThat(buncheol.isHost(999L)).isFalse();
    }
  }

  @Nested
  @DisplayName("모집 상태 검증 테스트")
  class ValidateRecruitingTest {

    @Test
    void RECRUITING_상태이고_마감전이면_유효하다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when & then
      assertThatCode(() -> buncheol.validateRecruiting(Instant.now())).doesNotThrowAnyException();
    }

    @Test
    void RECRUITING이_아니면_예외가_발생한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      setStatus(buncheol, BuncheolStatus.CANCELLED);

      // when & then
      assertThatThrownBy(() -> buncheol.validateRecruiting(Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    @Test
    void 마감일이_지났으면_RECRUITING이어도_예외가_발생한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      setDeadline(buncheol, Instant.now().minusSeconds(1));

      // when & then
      assertThatThrownBy(() -> buncheol.validateRecruiting(Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
  }

  @Nested
  @DisplayName("신규 참여 수용 여부 테스트")
  class AcceptsNewParticipationTest {

    private Buncheol buncheol(final BuncheolStatus status, final FlowType flowType) {
      Buncheol buncheol =
          Buncheol.create(
              HOST_ID,
              new BuncheolParams(
                  1L, "테스트 분철 제목", "분철 설명입니다.", "공식 스토어", FUTURE_DEADLINE, MIN_HEADCOUNT, 3000, null,
                  flowType, null),
              Instant.now());
      setStatus(buncheol, status);
      return buncheol;
    }

    @Test
    void 모집중이고_마감_전이면_받는다() {
      assertThat(buncheol(BuncheolStatus.RECRUITING, FlowType.C2C).acceptsNewParticipation(Instant.now()))
          .isTrue();
    }

    @Test
    void 모집중이어도_마감이_지났으면_받지_않는다() {
      Buncheol buncheol = buncheol(BuncheolStatus.RECRUITING, FlowType.C2C);
      setDeadline(buncheol, Instant.now().minusSeconds(1));

      assertThat(buncheol.acceptsNewParticipation(Instant.now())).isFalse();
    }

    @Test
    void 마감_시각_정각은_받지_않는다() {
      Buncheol buncheol = buncheol(BuncheolStatus.RECRUITING, FlowType.C2C);
      Instant deadline = Instant.now().plusSeconds(60);
      setDeadline(buncheol, deadline);

      assertThat(buncheol.acceptsNewParticipation(deadline)).isFalse();
    }

    // C2C 는 성사 확정 후 입금 수집중 구간에도 빈 슬롯 추가 모집을 받는다 (docs/46 §4.7-E1).
    @Test
    void 입금_수집중이면_C2C_만_받는다() {
      assertThat(
              buncheol(BuncheolStatus.PAYMENT_COLLECTING, FlowType.C2C)
                  .acceptsNewParticipation(Instant.now()))
          .isTrue();
      assertThat(
              buncheol(BuncheolStatus.PAYMENT_COLLECTING, FlowType.LEGACY)
                  .acceptsNewParticipation(Instant.now()))
          .isFalse();
    }

    @Test
    void 진행확정_취소_개최자취소는_받지_않는다() {
      assertThat(buncheol(BuncheolStatus.CONFIRMED, FlowType.C2C).acceptsNewParticipation(Instant.now()))
          .isFalse();
      assertThat(buncheol(BuncheolStatus.CANCELLED, FlowType.C2C).acceptsNewParticipation(Instant.now()))
          .isFalse();
      assertThat(
              buncheol(BuncheolStatus.HOST_CANCELLED, FlowType.C2C)
                  .acceptsNewParticipation(Instant.now()))
          .isFalse();
    }

    // 참여 가드(validateRecruiting)와 같은 술어여야 한다 — 갈리면 "화면엔 신청 가능한데 신청은 409" 가 재발한다.
    @Test
    void 모집중_구간에서는_참여_가드와_판정이_일치한다() {
      Buncheol beforeDeadline = buncheol(BuncheolStatus.RECRUITING, FlowType.LEGACY);
      Buncheol afterDeadline = buncheol(BuncheolStatus.RECRUITING, FlowType.LEGACY);
      setDeadline(afterDeadline, Instant.now().minusSeconds(1));

      assertThatCode(() -> beforeDeadline.validateRecruiting(Instant.now())).doesNotThrowAnyException();
      assertThat(beforeDeadline.acceptsNewParticipation(Instant.now())).isTrue();

      assertThatThrownBy(() -> afterDeadline.validateRecruiting(Instant.now()))
          .isInstanceOf(BusinessException.class);
      assertThat(afterDeadline.acceptsNewParticipation(Instant.now())).isFalse();
    }
  }

  @Nested
  @DisplayName("배송방법 지원 검증 테스트")
  class ValidateShippingMethodSupportedTest {

    @Test
    void 지원하는_배송방법이면_예외가_발생하지_않는다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when & then
      assertThatCode(() -> buncheol.validateShippingMethodSupported(ShippingMethod.GS25_HALF))
          .doesNotThrowAnyException();
    }

    @Test
    void 지원하지_않는_배송방법이면_예외가_발생한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when & then
      assertThatThrownBy(() -> buncheol.validateShippingMethodSupported(ShippingMethod.CU_HALF))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_SHIPPING_METHOD_NOT_SUPPORTED);
    }
  }

  @Nested
  @DisplayName("배송비 조회 테스트")
  class ShippingFeeForTest {

    @Test
    void 선택한_배송방법의_배송비를_반환한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when & then
      assertThat(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).isEqualTo(3000L);
    }
  }

  @Nested
  @DisplayName("배송비 0원 이벤트 판정 테스트")
  class IsFreeShippingEventTargetTest {

    private Buncheol buncheol(
        final FlowType flowType, final Integer gs25ShippingFee, final Integer cuShippingFee) {
      return Buncheol.create(
          HOST_ID,
          new BuncheolParams(
              1L,
              "테스트 분철 제목",
              "분철 설명입니다.",
              "공식 스토어",
              FUTURE_DEADLINE,
              MIN_HEADCOUNT,
              gs25ShippingFee,
              cuShippingFee,
              flowType,
              null),
          Instant.now());
    }

    @Test
    void 운영진_분철의_배송비가_모두_0원이면_이벤트_대상이다() {
      assertThat(buncheol(FlowType.LEGACY, 0, 0).isFreeShippingEventTarget()).isTrue();
      assertThat(buncheol(FlowType.LEGACY, 0, null).isFreeShippingEventTarget()).isTrue();
    }

    @Test
    void 운영진_분철이어도_유료_배송수단이_하나라도_있으면_대상이_아니다() {
      assertThat(buncheol(FlowType.LEGACY, 0, 3000).isFreeShippingEventTarget()).isFalse();
      assertThat(buncheol(FlowType.LEGACY, 3000, null).isFreeShippingEventTarget()).isFalse();
    }

    @Test
    void 사용자가_개최한_C2C_분철은_배송비가_0원이어도_대상이_아니다() {
      assertThat(buncheol(FlowType.C2C, 0, 0).isFreeShippingEventTarget()).isFalse();
    }
  }

  @Nested
  @DisplayName("성사 확정 선후 판정 테스트 (docs/56 H-09)")
  class IsCreatedBeforeFinalizeTest {

    private static final Instant FINALIZED_AT = Instant.parse("2026-05-14T12:00:00Z");

    private Buncheol finalizedBuncheol() {
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      setFinalizedAt(buncheol, FINALIZED_AT);
      return buncheol;
    }

    // 모집중에 신청(APPLIED)했다가 성사 확정 일괄 전이로 입금 대기가 된 참여.
    @Test
    void 성사_확정보다_먼저_만들어졌으면_true() {
      assertThat(finalizedBuncheol().isCreatedBeforeFinalize(FINALIZED_AT.minusSeconds(1))).isTrue();
    }

    // 입금 수집중 분철에 추가 모집으로 들어와 바로 입금 대기가 된 참여 (docs/46 §4.7-E1).
    @Test
    void 성사_확정_이후에_만들어졌으면_false() {
      assertThat(finalizedBuncheol().isCreatedBeforeFinalize(FINALIZED_AT.plusSeconds(1))).isFalse();
    }

    // created_at·finalized_at 은 둘 다 DATETIME(초 정밀도)이라 같은 초에 걸릴 수 있다. 동시각은
    // "확정을 거치지 않았다"(= 취소 허용)로 읽어, 성사 확정과 같은 초의 신청을 잠그지 않는다.
    @Test
    void 같은_시각이면_false_로_열어_둔다() {
      assertThat(finalizedBuncheol().isCreatedBeforeFinalize(FINALIZED_AT)).isFalse();
    }

    @Test
    void 아직_확정되지_않은_분철은_false() {
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      assertThat(buncheol.isCreatedBeforeFinalize(FINALIZED_AT.minusSeconds(1))).isFalse();
    }

    @Test
    void 생성_시각을_모르면_false() {
      assertThat(finalizedBuncheol().isCreatedBeforeFinalize(null)).isFalse();
    }
  }

  private void setFinalizedAt(final Buncheol buncheol, final Instant finalizedAt) {
    try {
      Field field = Buncheol.class.getDeclaredField("finalizedAt");
      field.setAccessible(true);
      field.set(buncheol, finalizedAt);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private void setStatus(final Buncheol buncheol, final BuncheolStatus status) {
    try {
      Field statusField = Buncheol.class.getDeclaredField("status");
      statusField.setAccessible(true);
      statusField.set(buncheol, status);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private void setDeadline(final Buncheol buncheol, final Instant deadline) {
    try {
      Field deadlineField = Buncheol.class.getDeclaredField("deadline");
      deadlineField.setAccessible(true);
      deadlineField.set(buncheol, deadline);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
