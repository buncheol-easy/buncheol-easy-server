package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PaybackTweetUrl 도메인 테스트")
class PaybackTweetUrlTest {

  @Nested
  @DisplayName("정규화 테스트")
  class NormalizeTest {

    @Test
    void 쿼리스트링을_제거한_퍼머링크로_정규화한다() {
      PaybackTweetUrl url =
          PaybackTweetUrl.parse("https://x.com/buncheol_fan/status/1234567890123456789?s=20&t=abc");

      assertThat(url.value()).isEqualTo("https://x.com/buncheol_fan/status/1234567890123456789");
    }

    @Test
    void 프래그먼트와_앞뒤_공백도_제거한다() {
      PaybackTweetUrl url =
          PaybackTweetUrl.parse("  https://twitter.com/fan/status/987654321#photo ");

      assertThat(url.value()).isEqualTo("https://twitter.com/fan/status/987654321");
    }

    @Test
    void 퍼머링크_뒤_추가_경로도_잘라낸다() {
      PaybackTweetUrl url = PaybackTweetUrl.parse("https://x.com/fan/status/123/photo/1");

      assertThat(url.value()).isEqualTo("https://x.com/fan/status/123");
    }
  }

  @Nested
  @DisplayName("형식 검증 테스트")
  class ValidateTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://x.com/fan/status/123",
          "https://twitter.com/Fan_123/status/1234567890123456789"
        })
    void x_com_과_twitter_com_트윗_퍼머링크를_허용한다(final String raw) {
      assertThat(PaybackTweetUrl.parse(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {
          "http://x.com/fan/status/123", // https 아님
          "https://mobile.x.com/fan/status/123", // 서브도메인 불허
          "https://x.com/fan/status/abc", // status id 가 숫자 아님
          "https://x.com/fan/status/123abc", // status id 뒤에 구분자 없이 문자가 붙음
          "https://x.com/fan", // status 경로 없음
          "https://instagram.com/p/abc123" // 타 서비스
        })
    void 트윗_퍼머링크_형식이_아니면_예외가_발생한다(final String raw) {
      assertThatThrownBy(() -> PaybackTweetUrl.parse(raw))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.PAYBACK_TWEET_URL_INVALID);
    }
  }
}
