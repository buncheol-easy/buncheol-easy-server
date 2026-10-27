package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.regex.Pattern;

public record PhoneNumber(String value) {

  public static final String REGEX = "^01\\d{9}$";
  private static final Pattern PATTERN = Pattern.compile(REGEX);
  // REGEX 의 자릿수와 같아야 한다. 길이 위반(USR-011)을 형식 위반(USR-012)과 나눠 알리려고 따로 둔다.
  private static final int LENGTH = 11;

  public PhoneNumber {
    validateValue(value);
  }

  public static PhoneNumber of(String value) {
    return new PhoneNumber(value);
  }

  public static boolean isValid(final String value) {
    return value != null && PATTERN.matcher(value).matches();
  }

  private void validateValue(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_REQUIRED);
    }
    if (value.length() != LENGTH) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_LENGTH_INVALID);
    }
    if (!isValid(value)) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_FORMAT_INVALID);
    }
  }
}
