package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.regex.Pattern;

public record PhoneNumber(String value) {

  private static final Pattern PHONE_NUMBER_REGEX = Pattern.compile("^01[0-9]+$");
  private static final int MIN_LENGTH = 10;
  private static final int MAX_LENGTH = 11;
  private static final int REGISTRATION_LENGTH = 11;

  public PhoneNumber {
    validateValue(value);
  }

  public static PhoneNumber of(String value) {
    return new PhoneNumber(value);
  }

  /**
   * 신규 입력에만 적용하는 11자리 규칙. 이 VO 는 record 라 JPA 가 조회 때도 생성자를 태우므로, 이 규칙을 생성자로 옮기면 기존 10자리 행이 엮인
   * 조회가 전부 실패한다 (BankAccount#validateForRegistration 과 같은 구조).
   */
  public static void validateForRegistration(final String value) {
    validateValue(value);
    if (value.length() != REGISTRATION_LENGTH) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_LENGTH_INVALID);
    }
  }

  private static void validateValue(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_REQUIRED);
    }
    if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_LENGTH_INVALID);
    }
    if (!PHONE_NUMBER_REGEX.matcher(value).matches()) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_FORMAT_INVALID);
    }
  }
}
