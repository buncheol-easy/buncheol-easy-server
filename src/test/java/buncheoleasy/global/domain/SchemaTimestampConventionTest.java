package buncheoleasy.global.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code updated_at} 은 DB 가 채운다는 규약을 스키마 파일에서 고정한다.
 *
 * <p>{@link TimestampedEntity#onUpdate()} 는 {@code @PreUpdate} 라 {@code @Modifying} bulk UPDATE 에서
 * 실행되지 않는다. 이 프로젝트는 상태 전이를 전부 CAS(bulk UPDATE)로 하고 운영 수동 SQL 도 쓰므로, 컬럼 속성으로
 * 내려야 어떤 경로로 UPDATE 가 들어와도 갱신 시각이 남는다. 실제로 이 규약 도입 전에 두 건이 조용히 빠져 있었다.
 *
 * <p>새 테이블을 추가할 때 빠뜨리는 것이 유일한 구멍이라 여기서 막는다. 앱이 {@code updated_at} 을 명시 대입하면
 * 그 값이 이기므로 기존 CAS 쿼리와 충돌하지 않는다.
 */
@DisplayName("스키마 updated_at 규약")
class SchemaTimestampConventionTest {

  // 예약어 테이블은 백틱으로 감싸여 있다 (`groups`).
  private static final Pattern TABLE =
      Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?`?([A-Za-z_][A-Za-z0-9_]*)`?");
  private static final Pattern UPDATED_AT_COLUMN = Pattern.compile("^\\s*updated_at\\s+\\S");

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"schema.sql", "schema-test.sql"})
  @DisplayName("모든 updated_at 컬럼에 ON UPDATE CURRENT_TIMESTAMP 가 있다")
  void updated_at_컬럼은_전부_ON_UPDATE_를_갖는다(final String resource) throws IOException {
    List<String> missing = new ArrayList<>();
    String table = "(unknown)";

    for (String line : readLines(resource)) {
      Matcher matcher = TABLE.matcher(line);
      if (matcher.find()) {
        table = matcher.group(1);
      }
      if (UPDATED_AT_COLUMN.matcher(line).find() && !line.contains("ON UPDATE CURRENT_TIMESTAMP")) {
        missing.add(table);
      }
    }

    assertThat(missing)
        .as(
            "%s 의 updated_at 에 ON UPDATE CURRENT_TIMESTAMP 가 없다 — bulk UPDATE(CAS)·수동 SQL 에서"
                + " 갱신 시각이 멈춘다. 운영 DB 에는 ALTER 도 함께 반영할 것",
            resource)
        .isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"schema.sql", "schema-test.sql"})
  @DisplayName("규약을 검사할 updated_at 컬럼이 실제로 존재한다 — 파서가 헛돌면 위 검사가 항상 통과한다")
  void updated_at_컬럼을_찾는다(final String resource) throws IOException {
    long count =
        readLines(resource).stream().filter(line -> UPDATED_AT_COLUMN.matcher(line).find()).count();

    assertThat(count).isGreaterThan(10L);
  }

  private List<String> readLines(final String resource) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(stream).as("%s 를 클래스패스에서 찾지 못했다", resource).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
    }
  }
}
