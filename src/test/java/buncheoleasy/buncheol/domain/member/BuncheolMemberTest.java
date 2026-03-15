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
  private static final String MEMBER_NAME = "멤버A";
  private static final long INSTANT_PRICE = 50_000L;

  @Nested
  @DisplayName("BuncheolMember 생성 테스트")
  class CreateTest {

    @Test
    void 입찰_불가_멤버_생성에_성공한다() {
      // when
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, INSTANT_PRICE, false, null);

      // then
      assertThat(member.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(member.getMemberName()).isEqualTo(MEMBER_NAME);
      assertThat(member.getInstantPrice()).isEqualTo(INSTANT_PRICE);
      assertThat(member.getBidOption().bidAllowed()).isFalse();
      assertThat(member.getBidOption().bidMinPrice()).isNull();
    }

    @Test
    void 입찰_가능_멤버_생성에_성공한다() {
      // given
      long bidMinPrice = 10_000L;

      // when
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, INSTANT_PRICE, true, bidMinPrice);

      // then
      assertThat(member.getBidOption().bidAllowed()).isTrue();
      assertThat(member.getBidOption().bidMinPrice()).isEqualTo(bidMinPrice);
    }

    @Test
    void 공식_그룹_멤버_ID가_있는_경우_생성에_성공한다() {
      // given
      Long groupMemberId = 10L;

      // when
      BuncheolMember member =
          BuncheolMember.create(
              BUNCHEOL_ID, groupMemberId, MEMBER_NAME, INSTANT_PRICE, false, null);

      // then
      assertThat(member.getMemberId()).isEqualTo(groupMemberId);
    }

    @Test
    void buncheolId가_null이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(
              () -> BuncheolMember.create(null, null, MEMBER_NAME, INSTANT_PRICE, false, null))
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
      // when & then
      assertThatThrownBy(
              () ->
                  BuncheolMember.create(BUNCHEOL_ID, null, memberName, INSTANT_PRICE, false, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 멤버명이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longName = "가".repeat(101);

      // when & then
      assertThatThrownBy(
              () -> BuncheolMember.create(BUNCHEOL_ID, null, longName, INSTANT_PRICE, false, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_NAME_LENGTH_INVALID);
    }

    @Test
    void 멤버명이_최대_길이_이하면_유효하다() {
      // given
      String maxLengthName = "가".repeat(100);

      // when & then
      assertThatCode(
              () ->
                  BuncheolMember.create(
                      BUNCHEOL_ID, null, maxLengthName, INSTANT_PRICE, false, null))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("즉시 구매 가격 검증 테스트")
  class ValidateInstantPriceTest {

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -50_000L})
    void 즉시_구매_가격이_0_이하면_예외가_발생한다(long price) {
      // when & then
      assertThatThrownBy(
              () -> BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, price, false, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);
    }

    @Test
    void 즉시_구매_가격이_양수면_유효하다() {
      // when & then
      assertThatCode(() -> BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, 1L, false, null))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("가격 수정 테스트")
  class UpdatePricingTest {

    @Test
    void 즉시구매가와_입찰옵션을_정상_수정한다() {
      // given
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, INSTANT_PRICE, false, null);

      // when
      member.updatePricing(60_000L, true, 20_000L);

      // then
      assertThat(member.getInstantPrice()).isEqualTo(60_000L);
      assertThat(member.getBidOption().bidAllowed()).isTrue();
      assertThat(member.getBidOption().bidMinPrice()).isEqualTo(20_000L);
    }

    @Test
    void 입찰_허용에서_비허용으로_변경한다() {
      // given
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, INSTANT_PRICE, true, 20_000L);

      // when
      member.updatePricing(INSTANT_PRICE, false, null);

      // then
      assertThat(member.getBidOption().bidAllowed()).isFalse();
      assertThat(member.getBidOption().bidMinPrice()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void 수정_즉시구매가가_0_이하면_예외가_발생한다(long price) {
      // given
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, INSTANT_PRICE, false, null);

      // when & then
      assertThatThrownBy(() -> member.updatePricing(price, false, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);
    }

    @Test
    void 제시최소금액이_즉시구매가_이상이면_예외가_발생한다() {
      // given
      BuncheolMember member =
          BuncheolMember.create(BUNCHEOL_ID, null, MEMBER_NAME, INSTANT_PRICE, false, null);

      // when & then
      assertThatThrownBy(() -> member.updatePricing(INSTANT_PRICE, true, INSTANT_PRICE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
    }
  }
}
