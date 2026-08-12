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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BankAccount VO 테스트")
class BankAccountTest {

  @Nested
  @DisplayName("정상 생성")
  class CreateTest {

    @Test
    void 유효한_값으로_생성된다() {
      BankAccount account = BankAccount.of("국민은행", "123456789012", "홍길동");

      assertThat(account.bank()).isEqualTo("국민은행");
      assertThat(account.account()).isEqualTo("123456789012");
      assertThat(account.holder()).isEqualTo("홍길동");
    }

    @ParameterizedTest
    @ValueSource(strings = {"110-123-456789", "3333-01-1234567", "123456789012"})
    void 하이픈이_포함된_계좌번호도_허용된다(String account) {
      assertThatCode(() -> BankAccount.of("국민은행", account, "홍길동")).doesNotThrowAnyException();
    }

    @Test
    void 최대_길이_경계값까지_허용된다() {
      String maxLengthText = "가".repeat(50);
      String maxLengthDigits = "1".repeat(50);

      assertThatCode(() -> BankAccount.of(maxLengthText, maxLengthDigits, maxLengthText))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("필수 필드 검증")
  class RequiredFieldTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 은행이_누락되면_예외가_발생한다(String bank) {
      assertThatThrownBy(() -> BankAccount.of(bank, "111", "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_REQUIRED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 계좌번호가_누락되면_예외가_발생한다(String account) {
      assertThatThrownBy(() -> BankAccount.of("국민은행", account, "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_REQUIRED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 예금주가_누락되면_예외가_발생한다(String holder) {
      assertThatThrownBy(() -> BankAccount.of("국민은행", "111", holder))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_REQUIRED);
    }
  }

  @Nested
  @DisplayName("길이 검증")
  class LengthTest {

    @Test
    void 은행명이_50자를_초과하면_예외가_발생한다() {
      String tooLong = "가".repeat(51);

      assertThatThrownBy(() -> BankAccount.of(tooLong, "111", "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_LENGTH_INVALID);
    }

    @Test
    void 계좌번호가_50자를_초과하면_예외가_발생한다() {
      String tooLong = "1".repeat(51);

      assertThatThrownBy(() -> BankAccount.of("국민은행", tooLong, "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_LENGTH_INVALID);
    }
  }

  @Nested
  @DisplayName("계좌번호 형식 검증")
  class AccountFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {"123 456", "123.456", "abc", "12a34", "１２３", "-123", "123-", "1--2"})
    void 계좌번호가_숫자와_하이픈_형식이_아니면_예외가_발생한다(String invalidAccount) {
      assertThatThrownBy(() -> BankAccount.of("국민은행", invalidAccount, "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_FORMAT_INVALID);
    }

    @Test
    void 예금주가_50자를_초과하면_예외가_발생한다() {
      String tooLong = "가".repeat(51);

      assertThatThrownBy(() -> BankAccount.of("국민은행", "111", tooLong))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_LENGTH_INVALID);
    }
  }

  @Nested
  @DisplayName("validateForRegistration — 신규 입력 전용 강화 검증 (docs/53 Q-02)")
  class ValidateForRegistrationTest {

    @Test
    void 계좌번호가_8자리_미만이면_예외가_발생한다() {
      assertThatThrownBy(() -> BankAccount.validateForRegistration("국민은행", "1234567", "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_TOO_SHORT);
    }

    @Test
    void 계좌번호가_정확히_8자리면_통과한다() {
      assertThatCode(() -> BankAccount.validateForRegistration("국민은행", "12345678", "홍길동"))
          .doesNotThrowAnyException();
    }

    // 하이픈 표기는 은행마다 달라 자릿수 판정에서 제외한다.
    @ParameterizedTest
    @ValueSource(strings = {"110-1234-567", "1-2-3-4-5-6-7-8"})
    void 하이픈을_제외한_숫자로_자릿수를_판정한다(String account) {
      assertThatCode(() -> BankAccount.validateForRegistration("국민은행", account, "홍길동"))
          .doesNotThrowAnyException();
    }

    @Test
    void 하이픈을_빼면_8자리_미만인_계좌번호는_거부한다() {
      assertThatThrownBy(() -> BankAccount.validateForRegistration("국민은행", "110-123-4", "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_TOO_SHORT);
    }

    // 이 VO 는 record 라 JPA 가 조회 시에도 생성자를 태운다. 기존에 저장된 짧은 계좌가
    // 조회만으로 깨지면 안 되므로, 강화 규칙은 validate() 가 아니라 이 메서드에만 있어야 한다.
    @Test
    void 기존_저장값_경로인_of_는_8자리_미만도_그대로_통과한다() {
      assertThatCode(() -> BankAccount.of("오", "111", "아아아")).doesNotThrowAnyException();
    }

    @Test
    void 형식_검증은_그대로_적용된다() {
      assertThatThrownBy(() -> BankAccount.validateForRegistration("국민은행", "abcdefgh", "홍길동"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_FORMAT_INVALID);
    }
  }
}
