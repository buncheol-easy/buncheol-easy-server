package buncheoleasy.group.infrastructure.member;

import buncheoleasy.group.domain.member.GroupMember;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupMemberMapper {

    int countByIdsAndGroupId(@Param("groupId") Long groupId, @Param("memberIds") List<Long> memberIds);

    List<GroupMember> selectAllByIds(@Param("memberIds") List<Long> memberIds);

    List<GroupMember> selectAllByGroupId(@Param("groupId") Long groupId);
}
