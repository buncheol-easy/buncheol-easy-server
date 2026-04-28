package buncheoleasy.buncheol.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;

/** Buncheol 모듈의 매퍼 테스트에서 FK 충족용으로 user 행을 직접 INSERT하기 위한 헬퍼. */
public final class TestUserFixture {

  private TestUserFixture() {}

  /**
   * users 테이블에 한 명을 INSERT하고 생성된 id를 반환한다. JPA 어댑터를 거치지 않고 raw SQL로 직접 삽입하므로 @MybatisTest 컨텍스트에서도
   * 사용 가능하다.
   */
  public static Long insertUser(JdbcTemplate jdbcTemplate, String providerId) {
    String nickname = "Guest" + providerId.replaceAll("[^a-zA-Z0-9]", "");
    jdbcTemplate.update(
        "INSERT INTO users (provider, provider_id, email, nickname, profile_completed)"
            + " VALUES (?, ?, ?, ?, ?)",
        "KAKAO",
        providerId,
        providerId + "@example.com",
        nickname,
        false);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE provider_id = ?", Long.class, providerId);
  }
}
