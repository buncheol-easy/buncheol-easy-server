package buncheoleasy.group.domain.member;

import java.util.List;

public interface GroupMemberRepository {

  List<GroupMember> findAllByGroupIdAndIds(Long groupId, List<Long> memberIds);

  List<GroupMember> findAllByGroupId(Long groupId);

  List<GroupMember> findAllByIds(List<Long> memberIds);

  List<GroupMember> findAllByName(String name);

  List<GroupMember> findAllByGroupIds(List<Long> groupIds);
}
