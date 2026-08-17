package buncheoleasy.global.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SearchText 정규화 테스트")
class SearchTextTest {

  @Nested
  @DisplayName("normalize")
  class NormalizeTest {

    @ParameterizedTest
    @CsvSource({
      "'스트레이 키즈', 스트레이키즈",
      "'스트레이키즈', 스트레이키즈",
      "'  아이브  앨범 ', 아이브앨범",
      "'Stray Kids', straykids",
      "'i-dle', idle",
      "'(여자)아이들', 여자아이들",
      "'NCT · DREAM', nctdream",
      "'세븐틴', 세븐틴"
    })
    void 공백과_구두점을_제거하고_소문자화한다(final String input, final String expected) {
      assertThat(SearchText.normalize(input)).isEqualTo(expected);
    }

    @Test
    void 전각_공백도_제거한다() {
      assertThat(SearchText.normalize("아이브　앨범")).isEqualTo("아이브앨범");
    }

    @Test
    void 공백_유무가_달라도_같은_문자열로_정규화된다() {
      assertThat(SearchText.normalize("스트레이 키즈")).isEqualTo(SearchText.normalize("스트레이키즈"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "-", " . ", "()"})
    @DisplayName("제거 대상만 남는 입력은 검색어 없음(null)으로 떨어진다")
    void 제거_후_비면_null(final String input) {
      assertThat(SearchText.normalize(input)).isNull();
    }

    @Test
    void null_은_null_이다() {
      assertThat(SearchText.normalize(null)).isNull();
    }
  }

  @Nested
  @DisplayName("normalizeForLike")
  class NormalizeForLikeTest {

    @Test
    void 정규화_후_LIKE_와일드카드를_이스케이프한다() {
      assertThat(SearchText.normalizeForLike("100% 아이브")).isEqualTo("100\\%아이브");
      assertThat(SearchText.normalizeForLike("아이브_앨범")).isEqualTo("아이브앨범");
    }

    @Test
    void 정규화_결과가_비면_null_이라_검색어_없음과_동일하게_다뤄진다() {
      assertThat(SearchText.normalizeForLike("   ")).isNull();
    }
  }
}
