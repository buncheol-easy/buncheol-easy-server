package buncheoleasy.group.infrastructure.member;

import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisGroupMemberRepository implements GroupMemberRepository {

    private final GroupMemberMapper groupMemberMapper;

    @Override
    public int countByIdsAndGroupId(final Long groupId, final List<Long> memberIds) {
        return groupMemberMapper.countByIdsAndGroupId(groupId, memberIds);
    }

    @Override
    public List<GroupMember> findAllByIds(final List<Long> memberIds) {
        return groupMemberMapper.selectAllByIds(memberIds);
    }

    @Override
    public List<GroupMember> findAllByGroupId(final Long groupId) {
        return groupMemberMapper.selectAllByGroupId(groupId);
    }
}
