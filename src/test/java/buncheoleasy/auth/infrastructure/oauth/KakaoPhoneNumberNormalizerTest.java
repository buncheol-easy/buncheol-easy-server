package buncheoleasy.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import buncheoleasy.user.domain.PhoneNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(strings = {"+82 10-1234-5678", "+82 16-1234-5678", "010-1234-5678"})
  void 정규화한_번호는_도메인_규칙을_통과한다(String rawValue) {
    String normalized = KakaoPhoneNumberNormalizer.normalize(rawValue);

    assertThatCode(() -> PhoneNumber.of(normalized)).doesNotThrowAnyException();
  }

  @Test
  void 휴대폰_번호가_11자리가_아니면_null을_반환한다() {
    assertThat(KakaoPhoneNumberNormalizer.normalize("+82 10-123-4567")).isNull();
    assertThat(KakaoPhoneNumberNormalizer.normalize("+82 16-123-4567")).isNull();
    assertThat(KakaoPhoneNumberNormalizer.normalize("010-123-4567")).isNull();
    assertThat(KakaoPhoneNumberNormalizer.normalize("+82 10-1234-56789")).isNull();
  }
}
