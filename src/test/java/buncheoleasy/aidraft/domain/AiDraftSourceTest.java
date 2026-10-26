package buncheoleasy.aidraft.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AiDraftSource 단위 테스트")
class AiDraftSourceTest {

  private static void assertInvalid(final String url, final String rawText) {
    assertThatThrownBy(() -> AiDraftSource.of(url, rawText))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_DRAFT_INVALID_INPUT);
  }

  @Nested
  @DisplayName("둘 중 정확히 하나")
  class ExactlyOneTest {

    @Test
    void 주소만_있으면_주소로_만든다() {
      AiDraftSource source = AiDraftSource.of("  https://www.ktown4u.com/iteminfo?goods_no=1  ", null);

      assertThat(source.type()).isEqualTo(AiDraftSourceType.URL);
      assertThat(source.url()).isEqualTo("https://www.ktown4u.com/iteminfo?goods_no=1");
      assertThat(source.rawText()).isNull();
    }

    @Test
    void 글만_있으면_글로_만든다() {
      AiDraftSource source = AiDraftSource.of(" ", "  투모로우바이투게더 앨범 14,100원  ");

      assertThat(source.type()).isEqualTo(AiDraftSourceType.TEXT);
      assertThat(source.rawText()).isEqualTo("투모로우바이투게더 앨범 14,100원");
      assertThat(source.url()).isNull();
    }

    @Test
    void 둘_다_있으면_거절한다() {
      assertInvalid("https://a.com", "글");
    }

    @Test
    void 둘_다_비어_있으면_거절한다() {
      assertInvalid(null, null);
      assertInvalid("   ", "\n\t");
    }
  }

  @Nested
  @DisplayName("주소 검사")
  class UrlTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "ftp://a.com/x",
          "javascript:alert(1)",
          "www.ktown4u.com",
          "https://",
          "https:///path",
          "https://a.com/상품 이름",
          "https://user@/x"
        })
    void http_https_가_아니거나_호스트가_없거나_공백이_있으면_거절한다(final String url) {
      assertInvalid(url, null);
    }

    @Test
    void 대문자_스킴과_한글_경로는_받는다() {
      // 엔진(httpx)은 한글이 그대로 든 주소도 받는다 — URI 로 엄격하게 거르지 않는다.
      assertThat(AiDraftSource.of("HTTPS://smartstore.naver.com/상품", null).type())
          .isEqualTo(AiDraftSourceType.URL);
    }

    @Test
    void 주소는_2000자까지_받는다() {
      String prefix = "https://a.com/";
      String max = prefix + "x".repeat(AiDraftSource.URL_MAX_LENGTH - prefix.length());

      assertThat(AiDraftSource.of(max, null).url()).hasSize(AiDraftSource.URL_MAX_LENGTH);
      assertInvalid(max + "x", null);
    }

    @Test
    void 기록용_호스트는_사용자정보와_포트를_떼고_소문자로_만든다() {
      assertThat(AiDraftSource.of("https://Shop.Weverse.io:443/ko/x?y=1", null).host())
          .isEqualTo("shop.weverse.io");
      assertThat(AiDraftSource.of("http://user:pw@a.com/x", null).host()).isEqualTo("a.com");
      assertThat(AiDraftSource.of("http://[::1]:8080/x", null).host()).isEqualTo("[::1]");
      assertThat(AiDraftSource.of("https://a.com?q=1", null).host()).isEqualTo("a.com");
    }
  }

  @Nested
  @DisplayName("붙여 넣은 글 검사")
  class RawTextTest {

    @Test
    void 글은_5000자까지_받고_길이만_기록한다() {
      String max = "가".repeat(AiDraftSource.RAW_TEXT_MAX_LENGTH);

      AiDraftSource source = AiDraftSource.of(null, max);

      assertThat(source.rawTextLength()).isEqualTo(AiDraftSource.RAW_TEXT_MAX_LENGTH);
      assertThat(source.host()).isNull();
      assertInvalid(null, max + "가");
    }
  }
}
