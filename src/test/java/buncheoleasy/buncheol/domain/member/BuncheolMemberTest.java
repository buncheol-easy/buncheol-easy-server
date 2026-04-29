package buncheoleasy.buncheol.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BuncheolMember 도메인 테스트")
class BuncheolMemberTest {

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long MEMBER_ID = 10L;
  private static final String MEMBER_NAME = "멤버A";
  private static final long BID_MIN_PRICE = 10_000L;

  @Nested
  @DisplayName("BuncheolMember 생성 테스트")
  class CreateTest {

    @Test
    void 정상_생성에_성공한다() {
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, MEMBER_NAME, null, BID_MIN_PRICE);

      assertThat(member.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(member.getMemberId()).isEqualTo(MEMBER_ID);
      assertThat(member.getMemberName()).isEqualTo(MEMBER_NAME);
      assertThat(member.getBidMinPrice()).isEqualTo(BID_MIN_PRICE);
    }

    @Test
    void buncheolId가_null이면_예외가_발생한다() {
      assertThatThrownBy(
              () -> BuncheolMember.create(null, MEMBER_ID, MEMBER_NAME, null, BID_MIN_PRICE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void memberId가_null이면_예외가_발생한다() {
      assertThatThrownBy(
              () -> BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, null, BID_MIN_PRICE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  @Nested
  @DisplayName("멤버명 검증 테스트")
  class ValidateMemberNameTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 멤버명이_null이거나_빈_값이면_예외가_발생한다(String memberName) {
      assertThatThrownBy(
              () -> BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, memberName, null, BID_MIN_PRICE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 멤버명이_최대_길이를_초과하면_예외가_발생한다() {
      String longName = "가".repeat(101);
      assertThatThrownBy(
              () -> BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, longName, null, BID_MIN_PRICE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_NAME_LENGTH_INVALID);
    }

    @Test
    void 멤버명이_최대_길이_이하면_유효하다() {
      String maxLengthName = "가".repeat(100);
      assertThatCode(
              () ->
                  BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, maxLengthName, null, BID_MIN_PRICE))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("제시 최소 금액 검증 테스트")
  class ValidateBidMinPriceTest {

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -50_000L})
    void 제시_최소_금액이_0_이하면_예외가_발생한다(long price) {
      assertThatThrownBy(
              () -> BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, MEMBER_NAME, null, price))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
    }

    @Test
    void 제시_최소_금액이_양수면_유효하다() {
      assertThatCode(() -> BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, MEMBER_NAME, null, 1L))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("제시 최소 금액 수정 테스트")
  class UpdateBidMinPriceTest {

    @Test
    void 정상_수정한다() {
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, MEMBER_NAME, null, BID_MIN_PRICE);

      member.updateBidMinPrice(20_000L);

      assertThat(member.getBidMinPrice()).isEqualTo(20_000L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void 수정_금액이_0_이하면_예외가_발생한다(long price) {
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, MEMBER_NAME, null, BID_MIN_PRICE);

      assertThatThrownBy(() -> member.updateBidMinPrice(price))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
    }
  }

  @Nested
  @DisplayName("제시 금액 검증 테스트")
  class ValidateBidAmountTest {

    @Test
    void 최소_금액보다_작으면_예외가_발생한다() {
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, MEMBER_NAME, null, BID_MIN_PRICE);

      assertThatThrownBy(() -> member.validateBidAmount(BID_MIN_PRICE - 1))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_BID_AMOUNT_INVALID);
    }

    @Test
    void 최소_금액과_같거나_크면_유효하다() {
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, MEMBER_NAME, null, BID_MIN_PRICE);

      assertThatCode(() -> member.validateBidAmount(BID_MIN_PRICE)).doesNotThrowAnyException();
      assertThatCode(() -> member.validateBidAmount(BID_MIN_PRICE * 100))
          .doesNotThrowAnyException();
    }
  }
}
