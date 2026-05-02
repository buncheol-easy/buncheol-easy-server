package buncheoleasy.buncheol.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;

/** Buncheol 모듈의 어댑터 테스트에서 FK 충족용 group/group_member 행을 만들기 위한 헬퍼. */
public final class TestGroupFixture {

  private TestGroupFixture() {}

  public static Long insertGroup(JdbcTemplate jdbcTemplate, String name) {
    jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", name);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM `groups` WHERE name = ? ORDER BY id DESC LIMIT 1", Long.class, name);
  }

  public static Long insertGroupMember(JdbcTemplate jdbcTemplate, Long groupId, String name) {
    jdbcTemplate.update("INSERT INTO group_members (group_id, name) VALUES (?, ?)", groupId, name);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM group_members WHERE group_id = ? AND name = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        groupId,
        name);
  }
}
