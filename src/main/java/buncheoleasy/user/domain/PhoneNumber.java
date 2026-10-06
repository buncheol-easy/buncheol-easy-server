package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.regex.Pattern;

public record PhoneNumber(String value) {

  private static final Pattern PHONE_NUMBER_REGEX = Pattern.compile("^01[0-9]+$");
  private static final int LENGTH = 11;

  public PhoneNumber {
    validateValue(value);
  }

  public static PhoneNumber of(String value) {
    return new PhoneNumber(value);
  }

  private void validateValue(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_REQUIRED);
    }
    if (value.length() != LENGTH) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_LENGTH_INVALID);
    }
    if (!PHONE_NUMBER_REGEX.matcher(value).matches()) {
      throw new BusinessException(ErrorCode.USER_PHONE_NUMBER_FORMAT_INVALID);
    }
  }
}
