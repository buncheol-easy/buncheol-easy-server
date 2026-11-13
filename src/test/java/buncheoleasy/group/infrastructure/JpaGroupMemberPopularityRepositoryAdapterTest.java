package buncheoleasy.group.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.group.domain.popularity.GroupMemberPopularity;
import buncheoleasy.group.domain.popularity.GroupMemberPopularityRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("멤버 인기 순위 조회 테스트")
class JpaGroupMemberPopularityRepositoryAdapterTest {

  @Autowired private GroupMemberPopularityRepository groupMemberPopularityRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long insertGroup(final String name) {
    jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", name);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM `groups` WHERE name = ? ORDER BY id DESC LIMIT 1", Long.class, name);
  }

  private Long insertMember(final Long groupId, final String name) {
    jdbcTemplate.update("INSERT INTO group_members (group_id, name) VALUES (?, ?)", groupId, name);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM group_members WHERE group_id = ? AND name = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        groupId,
        name);
  }

  private void insertRank(final Long memberId, final int rank) {
    jdbcTemplate.update(
        "INSERT INTO group_member_popularity (member_id, popularity_rank, source) VALUES (?, ?, ?)",
        memberId,
        rank,
        "테스트 순위 2026-10");
  }

  @Test
  void 그_그룹_멤버의_순위만_순위_오름차순으로_돌려준다() {
    final Long groupId = insertGroup("인기순위그룹");
    final Long low = insertMember(groupId, "낮은순위");
    final Long high = insertMember(groupId, "높은순위");
    insertMember(groupId, "순위밖");
    final Long otherGroupId = insertGroup("다른그룹");
    final Long other = insertMember(otherGroupId, "다른그룹멤버");
    insertRank(low, 40);
    insertRank(high, 2);
    insertRank(other, 1);

    final List<GroupMemberPopularity> result =
        groupMemberPopularityRepository.findAllByGroupId(groupId);

    assertThat(result).extracting(GroupMemberPopularity::getMemberId).containsExactly(high, low);
    assertThat(result).extracting(GroupMemberPopularity::getPopularityRank).containsExactly(2, 40);
  }

  @Test
  void 순위_자료가_없는_그룹이면_빈_목록이다() {
    final Long groupId = insertGroup("자료없는그룹");
    insertMember(groupId, "멤버");

    assertThat(groupMemberPopularityRepository.findAllByGroupId(groupId)).isEmpty();
  }
}
