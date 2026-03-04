package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Buncheol 도메인 테스트")
class BuncheolTest {

  private static final Long HOST_ID = 1L;
  private static final LocalDateTime FUTURE_DEADLINE = LocalDateTime.now().plusDays(7);

  private BuncheolParams validParams() {
    return new BuncheolParams(
        null,
        "테스트 그룹",
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 앨범",
        "공식 스토어",
        50_000L,
        FUTURE_DEADLINE,
        7,
        3000,
        null,
        "국민은행",
        "123-456-789012",
        "홍길동");
  }

  @Nested
  @DisplayName("Buncheol 생성 테스트")
  class CreateTest {

    @Test
    void 유효한_파라미터로_분철_생성에_성공한다() {
      // when
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams());

      // then
      assertThat(buncheol.getHostId()).isEqualTo(HOST_ID);
      assertThat(buncheol.getGroupName()).isEqualTo("테스트 그룹");
      assertThat(buncheol.getTitle()).isEqualTo("테스트 분철 제목");
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void hostId가_null이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> Buncheol.create(null, validParams()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void params가_null이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  @Nested
  @DisplayName("그룹명 검증 테스트")
  class ValidateGroupNameTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 그룹명이_null이거나_빈_값이면_예외가_발생한다(String groupName) {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null,
              groupName,
              "제목",
              null,
              "앨범명",
              "스토어명",
              50_000L,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 그룹명이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longGroupName = "가".repeat(101);
      BuncheolParams params =
          new BuncheolParams(
              null,
              longGroupName,
              "제목",
              null,
              "앨범명",
              "스토어명",
              50_000L,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
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
          new BuncheolParams(
              null,
              "그룹명",
              title,
              null,
              "앨범명",
              "스토어명",
              50_000L,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 제목이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longTitle = "가".repeat(201);
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              longTitle,
              null,
              "앨범명",
              "스토어명",
              50_000L,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
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
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              "앨범명",
              "스토어명",
              50_000L,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatCode(() -> Buncheol.create(HOST_ID, params)).doesNotThrowAnyException();
    }

    @Test
    void 설명이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longDescription = "가".repeat(301);
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              longDescription,
              "앨범명",
              "스토어명",
              50_000L,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  @Nested
  @DisplayName("굿즈명 검증 테스트")
  class ValidateGoodsNameTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 굿즈명이_null이거나_빈_값이면_예외가_발생한다(String goodsName) {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              goodsName,
              "스토어명",
              50_000L,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  @Nested
  @DisplayName("원가 검증 테스트")
  class ValidateOriginalPriceTest {

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -100_000L})
    void 원가가_0_이하면_예외가_발생한다(long price) {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              "앨범명",
              "스토어명",
              price,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_PRICE_INVALID);
    }

    @Test
    void 원가가_양수면_유효하다() {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              "앨범명",
              "스토어명",
              1L,
              FUTURE_DEADLINE,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatCode(() -> Buncheol.create(HOST_ID, params)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("발송 마감 일수 검증 테스트")
  class ValidateShippingDeadlineDaysTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -7})
    void 발송_마감_일수가_0_이하면_예외가_발생한다(int days) {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              "앨범명",
              "스토어명",
              50_000L,
              FUTURE_DEADLINE,
              days,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_SHIPPING_DEADLINE_DAYS_INVALID);
    }
  }

  @Nested
  @DisplayName("마감일 검증 테스트")
  class ValidateDeadlineTest {

    @Test
    void 마감일이_null이면_예외가_발생한다() {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null, "그룹명", "제목", null, "앨범명", "스토어명", 50_000L, null, 7, 3000, null, "국민은행",
              "123-456", "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_DEADLINE_INVALID);
    }

    @Test
    void 마감일이_현재보다_이전이면_예외가_발생한다() {
      // given
      LocalDateTime pastDeadline = LocalDateTime.now().minusDays(1);
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              "앨범명",
              "스토어명",
              50_000L,
              pastDeadline,
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatThrownBy(() -> Buncheol.create(HOST_ID, params))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_DEADLINE_INVALID);
    }

    @Test
    void 마감일이_현재보다_미래면_유효하다() {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              "앨범명",
              "스토어명",
              50_000L,
              LocalDateTime.now().plusSeconds(1),
              7,
              3000,
              null,
              "국민은행",
              "123-456",
              "홍길동");

      // when & then
      assertThatCode(() -> Buncheol.create(HOST_ID, params)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("초기 상태 테스트")
  class InitialStatusTest {

    @Test
    void 분철_생성_직후_상태는_RECRUITING이다() {
      // when
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams());

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
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams());

      // when & then
      assertThatCode(() -> buncheol.validateOwner(HOST_ID)).doesNotThrowAnyException();
    }

    @Test
    void 개최자가_아니면_예외가_발생한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams());

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
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams());

      // when
      buncheol.cancel();

      // then
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.CANCELLED);
    }

    @Test
    void 이미_CANCELLED_상태면_취소에_실패한다() {
      // given
      Buncheol buncheol = Buncheol.create(HOST_ID, validParams());
      buncheol.cancel();

      // when & then
      assertThatThrownBy(buncheol::cancel)
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
  }
}
