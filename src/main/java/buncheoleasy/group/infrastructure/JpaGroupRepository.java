package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaGroupRepository extends JpaRepository<Group, Long>, GroupRepository {

  @Query("SELECT g FROM Group g WHERE :keyword IS NULL OR g.name LIKE %:keyword%")
  List<Group> findByKeyword(@Param("keyword") String keyword);
}
