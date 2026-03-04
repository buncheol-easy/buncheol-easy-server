package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.regex.Pattern;

public record Nickname(String value) {

  private static final Pattern NICKNAME_REGEX = Pattern.compile("^[가-힣a-zA-Z0-9]+$");
  private static final int MAX_LENGTH = 20;

  public Nickname {
    validateValue(value);
  }

  public static Nickname of(String value) {
    return new Nickname(value);
  }

  private void validateValue(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.USER_NICKNAME_REQUIRED);
    }
    if (value.length() > MAX_LENGTH) {
      throw new BusinessException(ErrorCode.USER_NICKNAME_LENGTH_INVALID);
    }
    if (!NICKNAME_REGEX.matcher(value).matches()) {
      throw new BusinessException(ErrorCode.USER_NICKNAME_FORMAT_INVALID);
    }
  }
}
