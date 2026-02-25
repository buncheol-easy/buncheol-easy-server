package buncheoleasy.group.domain.member;

import java.util.List;

public interface GroupMemberRepository {

    int countByIdsAndGroupId(Long groupId, List<Long> memberIds);

    List<GroupMember> findAllByIds(List<Long> memberIds);

    List<GroupMember> findAllByGroupId(Long groupId);
}
