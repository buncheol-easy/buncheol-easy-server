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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Buncheol 도메인 테스트")
class BuncheolTest {

  private static final Long HOST_ID = 1L;
  private static final Instant FUTURE_DEADLINE = Instant.now().plus(7, ChronoUnit.DAYS);

  private BuncheolParams validParams() {
    return new BuncheolParams(1L, "테스트 분철 제목", "분철 설명입니다.", "공식 스토어", FUTURE_DEADLINE, 3000, null);
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
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
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
          new BuncheolParams(null, "제목", null, "스토어명", FUTURE_DEADLINE, 3000, null);

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
          new BuncheolParams(1L, title, null, "스토어명", FUTURE_DEADLINE, 3000, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 제목이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longTitle = "가".repeat(201);
      BuncheolParams params =
          new BuncheolParams(1L, longTitle, null, "스토어명", FUTURE_DEADLINE, 3000, null);

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
          new BuncheolParams(1L, "제목", null, "스토어명", FUTURE_DEADLINE, 3000, null);

      // when & then
      assertThatCode(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .doesNotThrowAnyException();
    }

    @Test
    void 설명이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longDescription = "가".repeat(301);
      BuncheolParams params =
          new BuncheolParams(1L, "제목", longDescription, "스토어명", FUTURE_DEADLINE, 3000, null);

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
      BuncheolParams params = new BuncheolParams(1L, "제목", null, "스토어명", null, 3000, null);

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
      BuncheolParams params = new BuncheolParams(1L, "제목", null, "스토어명", pastDeadline, 3000, null);

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params, Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_DEADLINE_INVALID);
    }

    @Test
    void 마감일이_현재보다_미래면_유효하다() {
      // given
      BuncheolParams params =
          new BuncheolParams(1L, "제목", null, "스토어명", Instant.now().plusSeconds(1), 3000, null);

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
  @DisplayName("취소 테스트")
  class CancelTest {

    @Test
    void RECRUITING_상태에서_취소하면_CANCELLED로_변경된다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());

      // when
      buncheol.cancel();

      // then
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.CANCELLED);
    }

    @Test
    void CLOSED_상태에서는_취소에_실패한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      setStatus(buncheol, BuncheolStatus.CLOSED);

      // when & then
      assertThatThrownBy(buncheol::cancel)
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }

    @Test
    void 이미_CANCELLED_상태면_취소에_실패한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      buncheol.cancel();

      // when & then
      assertThatThrownBy(buncheol::cancel)
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
  }

  @Nested
  @DisplayName("수동 마감 테스트")
  class CloseTest {

    @Test
    void RECRUITING_상태에서_마감하면_CLOSED와_closedAt이_세팅된다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      Instant now = Instant.parse("2026-05-27T12:00:00Z");

      // when
      buncheol.close(now);

      // then
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.CLOSED);
      assertThat(buncheol.getClosedAt()).isEqualTo(now);
    }

    @Test
    void deadline이_이미_지나도_RECRUITING이면_마감에_성공한다() {
      // given - 자동 마감 스케줄러 부재로 deadline 경과 후에도 RECRUITING 잔류 가능
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      setDeadline(buncheol, Instant.now().minusSeconds(1));
      Instant now = Instant.parse("2026-05-27T12:00:00Z");

      // when
      buncheol.close(now);

      // then
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.CLOSED);
      assertThat(buncheol.getClosedAt()).isEqualTo(now);
    }

    @ParameterizedTest
    @EnumSource(
        value = BuncheolStatus.class,
        names = {"RECRUITING"},
        mode = EnumSource.Mode.EXCLUDE)
    void RECRUITING이_아닌_상태에서는_마감에_실패한다(BuncheolStatus status) {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams(), Instant.now());
      setStatus(buncheol, status);

      // when & then
      assertThatThrownBy(() -> buncheol.close(Instant.now()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
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
