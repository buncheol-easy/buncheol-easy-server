package buncheoleasy.global.query;

/**
 * LIKE 와일드카드(`%`, `_`) 와 이스케이프 문자 자체(`\`)를 리터럴로 매칭하도록 이스케이프한다. 호출 측 JPQL 의 ESCAPE 절은 Java 리터럴
 * {@code "ESCAPE '\\'"} 로 작성한다 (실제 SQL 로 전달되는 이스케이프 문자는 단일 역슬래시 {@code \}).
 */
public final class LikeEscaper {

  private LikeEscaper() {}

  public static String escape(final String value) {
    if (value == null) {
      return null;
    }
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
