package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.Group;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaGroupRepository extends JpaRepository<Group, Long> {

  @Query(
      "SELECT g FROM Group g "
          + "WHERE :keyword IS NULL "
          + "  OR g.searchName LIKE CONCAT('%', :keyword, '%') ESCAPE '\\' "
          + "ORDER BY g.name")
  List<Group> findByNormalizedKeyword(@Param("keyword") String keyword);

  @Query(
      "SELECT g.id FROM Group g "
          + "WHERE :keyword IS NOT NULL "
          + "  AND g.searchName LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'")
  List<Long> findIdsByNormalizedKeyword(@Param("keyword") String keyword);
}
