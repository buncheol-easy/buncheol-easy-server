package buncheoleasy.global.query;

/**
 * 검색어 정규화. 공백·구두점을 제거하고 소문자화해 "스트레이 키즈" 와 "스트레이키즈" 가 같은 문자열이 되게 한다.
 *
 * <p><b>이 규칙은 세 곳에 동일하게 존재하며 반드시 함께 바뀌어야 한다.</b>
 *
 * <ol>
 *   <li>이 클래스 — 사용자가 입력한 <b>검색어</b>를 정규화한다.
 *   <li>{@code schema.sql} / {@code schema-test.sql} 의 {@code search_name}·{@code search_title}·{@code
 *       search_alias} 생성 컬럼 — 저장된 <b>대상 문자열</b>을 정규화한다 ({@link #STRIPPED_CHARACTERS} 와 같은 문자를 {@code
 *       REPLACE} 체인으로 제거).
 *   <li>프론트엔드 {@code lib/group-presenters.ts} 의 {@code normalizeGroupSearchText} — 클라이언트 랭킹용.
 * </ol>
 *
 * <p>정규화를 생성 컬럼으로 둔 이유: {@code groups}/{@code group_members}/{@code group_aliases} 는 애플리케이션 쓰기 경로가 없고
 * SQL 로 직접 시드된다. 값을 애플리케이션이 채우면 {@code search_name} 을 빠뜨린 INSERT 가 조용히 검색에서 누락되므로, DB 가 항상 계산하도록 맡긴다.
 */
public final class SearchText {

  /**
   * 제거 대상 문자. 공백류(space, full-width space, tab 등) 와 그룹·멤버명에 흔한 구두점을 제거한다. 생성 컬럼의 {@code REPLACE} 체인과
   * 1:1 로 대응한다.
   */
  public static final String STRIPPED_CHARACTERS = " 　._-()[]·";

  private SearchText() {}

  /**
   * 검색어를 정규화한다. {@code null} 이거나 정규화 결과가 빈 문자열이면 {@code null} 을 반환해, 호출 측이 "검색어 없음" 과 동일하게 다룰 수
   * 있게 한다 (예: {@code "  -  "} 같은 입력이 전체 조회로 떨어지지 않도록).
   */
  public static String normalize(final String value) {
    if (value == null) {
      return null;
    }

    final StringBuilder normalized = new StringBuilder(value.length());
    for (final char character : value.toCharArray()) {
      if (Character.isWhitespace(character) || STRIPPED_CHARACTERS.indexOf(character) >= 0) {
        continue;
      }
      normalized.append(Character.toLowerCase(character));
    }

    return normalized.isEmpty() ? null : normalized.toString();
  }

  /**
   * 정규화 후 LIKE 와일드카드까지 이스케이프한 값. {@code search_name}/{@code search_title} 컬럼과 {@code LIKE ... ESCAPE
   * '\'} 로 비교하는 모든 지점에서 이 메서드를 쓴다.
   */
  public static String normalizeForLike(final String value) {
    return LikeEscaper.escape(normalize(value));
  }
}
