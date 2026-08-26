package buncheoleasy.buncheol.domain.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CodeText 단위 테스트")
class CodeTextTest {

  @Nested
  @DisplayName("정규화 테스트")
  class ParseTest {

    @Test
    void 소문자를_대문자로_바꾼다() {
      assertThat(CodeText.parse("abcd2345").value()).isEqualTo("ABCD2345");
    }

    @Test
    void 하이픈과_공백을_제거한다() {
      assertThat(CodeText.parse(" ABCD-2345 ").value()).isEqualTo("ABCD2345");
    }

    @Test
    void 혼동_문자를_교정한다() {
      assertThat(CodeText.parse("ILO23456").value()).isEqualTo("11023456");
    }

    @Test
    void 교정과_구분자_제거를_함께_적용한다() {
      assertThat(CodeText.parse("il-o23 456").value()).isEqualTo("11023456");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC2345", "ABCD23456", "", "ABCD234!", "ABCDU345"})
    void 길이나_문자가_알파벳을_벗어나면_예외가_발생한다(final String raw) {
      assertThatThrownBy(() -> CodeText.parse(raw))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_INVALID);
    }

    @Test
    void null_이면_예외가_발생한다() {
      assertThatThrownBy(() -> CodeText.parse(null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_INVALID);
    }
  }
}
