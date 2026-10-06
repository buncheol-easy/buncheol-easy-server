package buncheoleasy.user.domain;

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

@DisplayName("PhoneNumber VO 테스트")
class PhoneNumberTest {

  @Nested
  @DisplayName("validateForRegistration — 신규 입력 전용 11자리 검증")
  class ValidateForRegistrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"0101234567", "0161234567"})
    void 번호가_10자리면_예외가_발생한다(String phoneNumber) {
      assertThatThrownBy(() -> PhoneNumber.validateForRegistration(phoneNumber))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PHONE_NUMBER_LENGTH_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01012345678", "01612345678"})
    void 번호가_11자리면_통과한다(String phoneNumber) {
      assertThatCode(() -> PhoneNumber.validateForRegistration(phoneNumber))
          .doesNotThrowAnyException();
    }

    @Test
    void 형식_검증은_그대로_적용된다() {
      assertThatThrownBy(() -> PhoneNumber.validateForRegistration("02012345678"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PHONE_NUMBER_FORMAT_INVALID);
    }

    @Test
    void 기존_저장값_경로인_of_는_10자리도_그대로_통과한다() {
      assertThat(PhoneNumber.of("0101234567").value()).isEqualTo("0101234567");
    }
  }
}
