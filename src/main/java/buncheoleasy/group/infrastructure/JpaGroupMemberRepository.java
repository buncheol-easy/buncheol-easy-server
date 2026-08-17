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

  // 정렬을 명시하지 않으면 아티스트 페이지의 멤버 칩 순서가 실행마다 달라질 수 있다.
  List<GroupMember> findAllByGroupIdOrderById(Long groupId);

  @Query(
      "SELECT gm FROM GroupMember gm "
          + "WHERE :name IS NOT NULL "
          + "  AND gm.searchName LIKE CONCAT('%', :name, '%') ESCAPE '\\' "
          + "ORDER BY gm.groupId, gm.id")
  List<GroupMember> findAllByNormalizedName(@Param("name") String name);

  @Query(
      "SELECT gm.id FROM GroupMember gm "
          + "WHERE :name IS NOT NULL "
          + "  AND gm.searchName LIKE CONCAT('%', :name, '%') ESCAPE '\\'")
  List<Long> findIdsByNormalizedName(@Param("name") String name);

  List<GroupMember> findAllByGroupIdIn(List<Long> groupIds);
}
