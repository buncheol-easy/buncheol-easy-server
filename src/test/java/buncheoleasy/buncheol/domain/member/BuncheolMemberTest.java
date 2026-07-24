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
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BuncheolMember 도메인 테스트")
class BuncheolMemberTest {

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long MEMBER_ID = 10L;
  private static final long PRICE = 10_000L;

  @Nested
  @DisplayName("BuncheolMember 생성 테스트")
  class CreateTest {

    @Test
    void 정상_생성에_성공한다() {
      BuncheolMember member = BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, PRICE);

      assertThat(member.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(member.getMemberId()).isEqualTo(MEMBER_ID);
      assertThat(member.getPrice()).isEqualTo(PRICE);
    }

    @Test
    void buncheolId가_null이면_예외가_발생한다() {
      assertThatThrownBy(() -> BuncheolMember.create(null, MEMBER_ID, PRICE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void memberId가_null이면_예외가_발생한다() {
      assertThatThrownBy(() -> BuncheolMember.create(BUNCHEOL_ID, null, PRICE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  @Nested
  @DisplayName("멤버 금액 검증 테스트")
  class ValidatePriceTest {

    @ParameterizedTest
    @ValueSource(longs = {-1L, -100L, -50_000L})
    void 금액이_음수면_예외가_발생한다(long price) {
      assertThatThrownBy(() -> BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, price))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 99L, 150L, 10_050L})
    void 금액이_100원_단위가_아니면_예외가_발생한다(long price) {
      assertThatThrownBy(() -> BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, price))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);
    }

    // 0원은 오픈 이벤트 무료 분철(운영진 발행) 슬롯으로 허용한다.
    @ParameterizedTest
    @ValueSource(longs = {0L, 100L, 10_000L, 31_900L})
    void 금액이_100원_단위의_0_이상이면_유효하다(long price) {
      assertThatCode(() -> BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, price))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("멤버 금액 수정 테스트")
  class UpdatePriceTest {

    @Test
    void 정상_수정한다() {
      BuncheolMember member = BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, PRICE);

      member.updatePrice(20_000L);

      assertThat(member.getPrice()).isEqualTo(20_000L);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -100L, 150L})
    void 수정_금액이_유효하지_않으면_예외가_발생한다(long price) {
      BuncheolMember member = BuncheolMember.create(BUNCHEOL_ID, MEMBER_ID, PRICE);

      assertThatThrownBy(() -> member.updatePrice(price))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);
    }
  }
}
