package buncheoleasy.group.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.global.query.SearchText;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("그룹·멤버 검색 정규화 테스트")
class JpaGroupRepositoryAdapterTest {

  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long insertGroup(final String name) {
    jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", name);
    em.clear();
    return jdbcTemplate.queryForObject(
        "SELECT id FROM `groups` WHERE name = ? ORDER BY id DESC LIMIT 1", Long.class, name);
  }

  private Long insertMember(final Long groupId, final String name) {
    jdbcTemplate.update(
        "INSERT INTO group_members (group_id, name) VALUES (?, ?)", groupId, name);
    em.clear();
    return jdbcTemplate.queryForObject(
        "SELECT id FROM group_members WHERE group_id = ? AND name = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        groupId,
        name);
  }

  private String storedGroupSearchName(final Long groupId) {
    return jdbcTemplate.queryForObject(
        "SELECT search_name FROM `groups` WHERE id = ?", String.class, groupId);
  }

  @Nested
  @DisplayName("생성 컬럼 정규화 파리티")
  class NormalizationParityTest {

    /**
     * 정규화 규칙은 Java({@link SearchText})·{@code schema.sql}·{@code schema-test.sql}·마이그레이션
     * 스크립트·프론트에 각각 사본으로 존재한다. 하나만 바뀌면 검색이 조용히 어긋나므로, 최소한 Java 와 DB 생성 컬럼이
     * 같은 결과를 내는지는 CI 에서 잡는다.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
          "스트레이 키즈",
          "(여자)아이들",
          "NCT · DREAM",
          "i-dle",
          "Stray Kids",
          "a[b]c_d.e",
          "아이브　앨범"
        })
    void 생성컬럼_정규화가_SearchText_와_일치한다(final String name) {
      final Long groupId = insertGroup(name);

      assertThat(storedGroupSearchName(groupId)).isEqualTo(SearchText.normalize(name));
    }
  }

  @Nested
  @DisplayName("그룹 검색")
  class SearchGroupTest {

    @Test
    void 공백_유무와_무관하게_같은_그룹이_검색된다() {
      insertGroup("스트레이 키즈");

      final List<Group> spaced =
          groupRepository.findByNormalizedKeyword(SearchText.normalizeForLike("스트레이 키즈"));
      final List<Group> unspaced =
          groupRepository.findByNormalizedKeyword(SearchText.normalizeForLike("스트레이키즈"));

      assertThat(spaced).extracting(Group::getName).contains("스트레이 키즈");
      assertThat(unspaced).extracting(Group::getName).contains("스트레이 키즈");
    }

    @Test
    void 괄호를_뺀_검색어로도_찾을_수_있다() {
      insertGroup("(여자)아이들");

      assertThat(groupRepository.findByNormalizedKeyword(SearchText.normalizeForLike("여자아이들")))
          .extracting(Group::getName)
          .contains("(여자)아이들");
    }

    @Test
    void keyword_가_null_이면_전체_그룹을_반환한다() {
      insertGroup("전체조회용 그룹");

      assertThat(groupRepository.findByNormalizedKeyword(null)).isNotEmpty();
    }

    @Test
    void 매칭_id_조회는_null_검색어에_아무것도_반환하지_않는다() {
      insertGroup("널가드 확인용 그룹");

      // MySQL 은 CONCAT('%', NULL, '%') 를 NULL 로, H2 는 '%%' 로 접는다. 가드가 빠지면 H2 에서만 전건이 된다.
      assertThat(groupRepository.findIdsByNormalizedKeyword(null)).isEmpty();
    }
  }

  @Nested
  @DisplayName("멤버 검색")
  class SearchMemberTest {

    @Test
    void 공백이_들어간_멤버명도_정규화되어_검색된다() {
      final Long groupId = insertGroup("멤버검색 그룹");
      insertMember(groupId, "장 원영");

      assertThat(
              groupMemberRepository.findAllByNormalizedName(
                  SearchText.normalizeForLike("장원영")))
          .extracting(GroupMember::getName)
          .contains("장 원영");
    }

    @Test
    void 부분_일치로도_검색된다() {
      final Long groupId = insertGroup("부분일치 그룹");
      insertMember(groupId, "안유진");

      assertThat(
              groupMemberRepository.findAllByNormalizedName(SearchText.normalizeForLike("유진")))
          .extracting(GroupMember::getName)
          .contains("안유진");
    }

    @Test
    void null_검색어는_아무것도_반환하지_않는다() {
      final Long groupId = insertGroup("널가드 멤버 그룹");
      insertMember(groupId, "널가드멤버");

      assertThat(groupMemberRepository.findAllByNormalizedName(null)).isEmpty();
      assertThat(groupMemberRepository.findIdsByNormalizedName(null)).isEmpty();
    }

    @Test
    void 그룹_멤버는_id_오름차순으로_반환된다() {
      final Long groupId = insertGroup("정렬확인 그룹");
      final Long first = insertMember(groupId, "먼저");
      final Long second = insertMember(groupId, "나중");

      assertThat(groupMemberRepository.findAllByGroupId(groupId))
          .extracting(GroupMember::getId)
          .containsExactly(first, second);
    }
  }
}
