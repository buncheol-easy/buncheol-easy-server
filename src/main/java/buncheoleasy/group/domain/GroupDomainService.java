package buncheoleasy.group.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupDomainService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    public void validateGroupExists(final Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
        }
    }

    public Group getGroup(final Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    public void validateMembersBelongToGroup(final Long id, final List<Long> memberIds) {
        int matchedCount = groupMemberRepository.countByIdsAndGroupId(id, memberIds);
        if (matchedCount != memberIds.size()) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP);
        }
    }

    public List<Group> searchGroups(final String keyword) {
        return groupRepository.findByKeyword(keyword);
    }

    public List<GroupMember> getGroupMembers(final Long id) {
        return groupMemberRepository.findAllByGroupId(id);
    }

    public List<GroupMember> getGroupMembersByIds(final List<Long> memberIds) {
        return groupMemberRepository.findAllByIds(memberIds);
    }
}
