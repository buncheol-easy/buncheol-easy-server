package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.regex.Pattern;

public record Email(String value) {

  private static final Pattern EMAIL_REGEX =
      Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
  private static final int MAX_LENGTH = 320;

  public Email {
    validateValue(value);
  }

  public static Email of(final String value) {
    return new Email(value);
  }

  private void validateValue(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.USER_EMAIL_REQUIRED);
    }
    if (value.length() > MAX_LENGTH) {
      throw new BusinessException(ErrorCode.USER_EMAIL_LENGTH_INVALID);
    }
    if (!EMAIL_REGEX.matcher(value).matches()) {
      throw new BusinessException(ErrorCode.USER_EMAIL_FORMAT_INVALID);
    }
  }
}
