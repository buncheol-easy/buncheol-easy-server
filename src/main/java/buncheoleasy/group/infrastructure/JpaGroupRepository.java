package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.Group;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaGroupRepository extends JpaRepository<Group, Long> {

  /**
   * 그룹명 또는 별칭({@code group_aliases}) 부분일치. 두 컬럼 모두 같은 규칙으로 정규화돼 있어 검색어도 정규화본을 넘긴다.
   *
   * <p>{@code LIKE '%..%'} 라 정규화 컬럼의 인덱스로 시크하지 못하고 스캔이지만, 그룹 수백 행·별칭 수천 행 규모라 무시할 만하다.
   */
  @Query(
      "SELECT g FROM Group g "
          + "WHERE :keyword IS NULL "
          + "  OR g.searchName LIKE CONCAT('%', :keyword, '%') ESCAPE '\\' "
          + "  OR EXISTS (SELECT ga FROM GroupAlias ga "
          + "             WHERE ga.groupId = g.id "
          + "             AND ga.searchAlias LIKE CONCAT('%', :keyword, '%') ESCAPE '\\') "
          + "ORDER BY g.name")
  List<Group> findByNormalizedKeyword(@Param("keyword") String keyword);

  /**
   * 분철 검색이 그룹명·별칭까지 커버하기 위한 사전 해석용.
   *
   * <p>{@code IS NOT NULL} 가드는 생략할 수 없다 — MySQL 은 {@code CONCAT('%', NULL, '%')} 를 NULL 로, H2 는 {@code
   * '%%'} 로 접어 결과가 정반대가 된다.
   */
  @Query(
      "SELECT g.id FROM Group g "
          + "WHERE :keyword IS NOT NULL "
          + "  AND (g.searchName LIKE CONCAT('%', :keyword, '%') ESCAPE '\\' "
          + "       OR EXISTS (SELECT ga FROM GroupAlias ga "
          + "                  WHERE ga.groupId = g.id "
          + "                  AND ga.searchAlias LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'))")
  List<Long> findIdsByNormalizedKeyword(@Param("keyword") String keyword);
}
