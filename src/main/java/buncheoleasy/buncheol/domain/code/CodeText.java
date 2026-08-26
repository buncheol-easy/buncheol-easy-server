package buncheoleasy.buncheol.domain.code;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;

/**
 * 참여 코드 문자열의 정규화본. 알파벳은 Crockford base32 (혼동 쌍 {@code I L O U} 제외) — DM 으로 받아 손으로 옮겨 적는
 * 코드라 {@code 0/O}, {@code 1/I/L} 오타를 입력 시 되돌려 매핑한다.
 */
public record CodeText(String value) {

  static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  private static final int LENGTH = 8;

  public CodeText {
    if (value == null || value.length() != LENGTH) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_INVALID);
    }
    for (int i = 0; i < value.length(); i++) {
      if (ALPHABET.indexOf(value.charAt(i)) < 0) {
        throw new BusinessException(ErrorCode.PARTICIPATION_CODE_INVALID);
      }
    }
  }

  /** 형식 오류는 미존재 코드와 같은 에러로 끝낸다 — 유효 코드 공간을 좁히는 추측을 돕지 않기 위함이다. */
  public static CodeText parse(final String raw) {
    if (raw == null) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_INVALID);
    }
    StringBuilder normalized = new StringBuilder(LENGTH);
    for (char c : raw.toUpperCase().toCharArray()) {
      if (c == '-' || Character.isWhitespace(c)) {
        continue;
      }
      normalized.append(switch (c) {
        case 'I', 'L' -> '1';
        case 'O' -> '0';
        default -> c;
      });
    }
    return new CodeText(normalized.toString());
  }
}
