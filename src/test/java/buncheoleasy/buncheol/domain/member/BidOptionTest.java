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

@DisplayName("BidOption 도메인 테스트")
class BidOptionTest {

    private static final long INSTANT_PRICE = 50_000L;

    @Nested
    @DisplayName("입찰 불가 옵션 테스트")
    class BidNotAllowedTest {

        @Test
        void 입찰_불가_설정_시_bidMinPrice가_null이면_생성에_성공한다() {
            // when
            BidOption option = BidOption.of(INSTANT_PRICE, false, null);

            // then
            assertThat(option.bidAllowed()).isFalse();
            assertThat(option.bidMinPrice()).isNull();
        }

        @Test
        void 입찰_불가인데_bidMinPrice가_있으면_예외가_발생한다() {
            // when & then
            assertThatThrownBy(() -> BidOption.of(INSTANT_PRICE, false, 10_000L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("입찰 가능 옵션 테스트")
    class BidAllowedTest {

        @Test
        void 입찰_가능_설정_시_유효한_bidMinPrice로_생성에_성공한다() {
            // given
            long bidMinPrice = 10_000L;

            // when
            BidOption option = BidOption.of(INSTANT_PRICE, true, bidMinPrice);

            // then
            assertThat(option.bidAllowed()).isTrue();
            assertThat(option.bidMinPrice()).isEqualTo(bidMinPrice);
        }

        @Test
        void 입찰_가능인데_bidMinPrice가_null이면_예외가_발생한다() {
            // when & then
            assertThatThrownBy(() -> BidOption.of(INSTANT_PRICE, true, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_REQUIRED);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -10_000L})
        void bidMinPrice가_0_이하면_예외가_발생한다(long bidMinPrice) {
            // when & then
            assertThatThrownBy(() -> BidOption.of(INSTANT_PRICE, true, bidMinPrice))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
        }

        @Test
        void bidMinPrice가_즉시구매가와_같으면_예외가_발생한다() {
            // when & then
            assertThatThrownBy(() -> BidOption.of(INSTANT_PRICE, true, INSTANT_PRICE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
        }

        @Test
        void bidMinPrice가_즉시구매가보다_크면_예외가_발생한다() {
            // given
            long bidMinPrice = INSTANT_PRICE + 1;

            // when & then
            assertThatThrownBy(() -> BidOption.of(INSTANT_PRICE, true, bidMinPrice))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
        }

        @Test
        void bidMinPrice가_즉시구매가보다_작으면_유효하다() {
            // given
            long bidMinPrice = INSTANT_PRICE - 1;

            // when & then
            assertThatCode(() -> BidOption.of(INSTANT_PRICE, true, bidMinPrice))
                    .doesNotThrowAnyException();
        }
    }
}
