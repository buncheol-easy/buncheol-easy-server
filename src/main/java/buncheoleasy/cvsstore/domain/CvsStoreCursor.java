package buncheoleasy.cvsstore.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;

/**
 * 접수처 검색 전용 keyset 커서. 정렬 키는 {@code (그룹 순위, id)} 2-튜플이다.
 *
 * <ul>
 *   <li>그룹 순위: 지점명 일치 = {@value #RANK_NAME}, 주소만 일치 = {@value #RANK_ADDRESS}. "강남" 검색 시 강남 지점명 매장이
 *       강남대로 소재 매장보다 먼저 노출되게 한다. 키워드가 없으면 전체가 {@value #RANK_NAME} 그룹이다.
 *   <li>id: 그룹 내 오름차순 tie-break.
 * </ul>
 *
 * <p>인코딩 형식: {@code <rank>_<id>} (예: {@code 0_42}). 첫 페이지는 {@link #firstPage()} 또는 {@code
 * parse(null)} / {@code parse("")} 로 표현하며 모든 필드가 {@code null} 이다.
 */
public record CvsStoreCursor(Integer groupRank, Long id) {

  /** 지점명 일치 그룹 순위 — 항상 주소 일치 그룹보다 먼저 노출된다. 키워드 미입력 시 전체가 이 그룹이다. */
  public static final int RANK_NAME = 0;

  /** 주소만 일치(지점명 불일치) 그룹 순위 — 맨 뒤. */
  public static final int RANK_ADDRESS = 1;

  private static final String DELIMITER = "_";
  private static final int EXPECTED_PARTS = 2;
  private static final int RANK_INDEX = 0;
  private static final int ID_INDEX = 1;

  public static CvsStoreCursor firstPage() {
    return new CvsStoreCursor(null, null);
  }

  public static CvsStoreCursor parse(final String raw) {
    if (raw == null || raw.isBlank()) {
      return firstPage();
    }
    final String[] parts = raw.split(DELIMITER);
    if (parts.length != EXPECTED_PARTS) {
      throw new BusinessException(ErrorCode.CURSOR_INVALID);
    }
    try {
      final int rank = Integer.parseInt(parts[RANK_INDEX]);
      if (rank != RANK_NAME && rank != RANK_ADDRESS) {
        throw new BusinessException(ErrorCode.CURSOR_INVALID);
      }
      return new CvsStoreCursor(rank, Long.parseLong(parts[ID_INDEX]));
    } catch (final NumberFormatException ex) {
      throw new BusinessException(ErrorCode.CURSOR_INVALID, ex);
    }
  }

  /**
   * 직전 페이지의 마지막 접수처로부터 다음 커서를 만든다. 그룹 판정은 지점명의 키워드 포함 여부(대소문자 무시 — MySQL {@code utf8mb4_unicode_ci}
   * 매칭과 정합)로 하며, {@code trimmedKeyword} 는 이스케이프 전의 원문이어야 한다.
   */
  public static CvsStoreCursor from(final CvsStore store, final String trimmedKeyword) {
    if (trimmedKeyword == null || containsIgnoreCase(store.getName(), trimmedKeyword)) {
      return new CvsStoreCursor(RANK_NAME, store.getId());
    }
    return new CvsStoreCursor(RANK_ADDRESS, store.getId());
  }

  public boolean isFirstPage() {
    return groupRank == null;
  }

  /** 커서가 지점명 일치 그룹을 가리키는지 여부. 첫 페이지({@code groupRank == null})는 false. */
  public boolean isNameGroup() {
    return groupRank != null && groupRank == RANK_NAME;
  }

  /** 호출 측은 {@link #isFirstPage()} 가 false 인 인스턴스만 넘긴다 (hasNext 분기 후 호출). */
  public String encode() {
    return groupRank + DELIMITER + id;
  }

  private static boolean containsIgnoreCase(final String source, final String target) {
    return source.toLowerCase().contains(target.toLowerCase());
  }
}
