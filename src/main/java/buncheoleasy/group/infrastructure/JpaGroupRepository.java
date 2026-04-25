package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.Group;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaGroupRepository extends JpaRepository<Group, Long> {

  @Query("SELECT g FROM Group g WHERE :keyword IS NULL OR g.name LIKE %:keyword%")
  List<Group> findByKeyword(@Param("keyword") String keyword);
}
