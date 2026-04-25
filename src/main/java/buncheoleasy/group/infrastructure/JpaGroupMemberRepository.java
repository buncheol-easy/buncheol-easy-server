package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.member.GroupMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaGroupMemberRepository extends JpaRepository<GroupMember, Long> {

  @Query("SELECT gm FROM GroupMember gm WHERE gm.groupId = :groupId AND gm.id IN :ids")
  List<GroupMember> findAllByGroupIdAndIds(
      @Param("groupId") Long groupId, @Param("ids") List<Long> ids);

  List<GroupMember> findAllByGroupId(Long groupId);
}
