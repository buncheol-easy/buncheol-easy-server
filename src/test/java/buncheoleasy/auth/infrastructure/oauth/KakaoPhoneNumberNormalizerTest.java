package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KakaoPhoneNumberNormalizer 단위 테스트")
class KakaoPhoneNumberNormalizerTest {

  @Test
  void 카카오_국제_형식을_국내_형식으로_변환한다() {
    assertThat(KakaoPhoneNumberNormalizer.normalize("+82 10-1234-5678")).isEqualTo("01012345678");
    assertThat(KakaoPhoneNumberNormalizer.normalize("+821012345678")).isEqualTo("01012345678");
  }

  @Test
  void 이미_국내_형식이면_숫자만_남긴다() {
    assertThat(KakaoPhoneNumberNormalizer.normalize("010-1234-5678")).isEqualTo("01012345678");
    assertThat(KakaoPhoneNumberNormalizer.normalize("01012345678")).isEqualTo("01012345678");
  }

  @Test
  void 국내_휴대폰_형식이_아니면_null을_반환한다() {
    assertThat(KakaoPhoneNumberNormalizer.normalize("+1 415-555-0100")).isNull();
    assertThat(KakaoPhoneNumberNormalizer.normalize("02-123-4567")).isNull();
    assertThat(KakaoPhoneNumberNormalizer.normalize("")).isNull();
    assertThat(KakaoPhoneNumberNormalizer.normalize(null)).isNull();
  }
}
