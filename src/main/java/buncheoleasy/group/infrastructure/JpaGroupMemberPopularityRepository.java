package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.popularity.GroupMemberPopularity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaGroupMemberPopularityRepository extends JpaRepository<GroupMemberPopularity, Long> {

  @Query(
      "SELECT p FROM GroupMemberPopularity p "
          + "WHERE p.memberId IN (SELECT gm.id FROM GroupMember gm WHERE gm.groupId = :groupId) "
          + "ORDER BY p.popularityRank, p.memberId")
  List<GroupMemberPopularity> findAllByGroupId(@Param("groupId") Long groupId);
}
