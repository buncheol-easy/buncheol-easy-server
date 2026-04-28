package buncheoleasy.buncheol.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;

/** Buncheol 모듈의 어댑터 테스트에서 FK 충족용 user 행을 만들기 위한 헬퍼. */
public final class TestUserFixture {

  private TestUserFixture() {}

  /**
   * users 테이블에 한 명을 INSERT 하고 생성된 id 를 반환한다. JPA 어댑터(JpaUserRepositoryAdapter) 를 통하지 않고 raw SQL 로
   * 직접 삽입한다 — Buncheol 모듈 테스트 입장에서는 user 의 도메인 검증·이벤트가 불필요하므로 FK 만 만족하는 최소 행을 빠르게 만든다.
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
