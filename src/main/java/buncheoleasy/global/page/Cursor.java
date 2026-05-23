package buncheoleasy.global.page;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;

/**
 * `createdAt DESC, id DESC` 정렬 기준의 커서.
 *
 * <p>인코딩 형식: {@code <Instant ISO-8601>_<id>} (예: {@code 2026-05-21T00:00:00Z_42}). 첫 페이지는 {@link
 * #firstPage()} 또는 {@code parse(null)} / {@code parse("")} 로 표현하며 {@code createdAt == null && id ==
 * null} 이다.
 */
public record Cursor(Instant createdAt, Long id) {

  private static final String DELIMITER = "_";
  private static final int EXPECTED_PARTS = 2;
  private static final int CREATED_AT_INDEX = 0;
  private static final int ID_INDEX = 1;

  public static Cursor firstPage() {
    return new Cursor(null, null);
  }

  public static Cursor parse(final String raw) {
    if (raw == null || raw.isBlank()) {
      return firstPage();
    }
    final String[] parts = raw.split(DELIMITER);
    if (parts.length != EXPECTED_PARTS) {
      throw new BusinessException(ErrorCode.CURSOR_INVALID);
    }
    try {
      return new Cursor(Instant.parse(parts[CREATED_AT_INDEX]), Long.parseLong(parts[ID_INDEX]));
    } catch (final RuntimeException ex) {
      throw new BusinessException(ErrorCode.CURSOR_INVALID, ex);
    }
  }

  public static Cursor from(final Cursorable cursorable) {
    return new Cursor(cursorable.getCreatedAt(), cursorable.getId());
  }

  public boolean isFirstPage() {
    return createdAt == null && id == null;
  }

  /** 호출 측은 {@link #isFirstPage()} 가 false 인 인스턴스만 넘긴다 (hasNext 분기 후 호출). */
  public String encode() {
    return createdAt.toString() + DELIMITER + id;
  }
}
