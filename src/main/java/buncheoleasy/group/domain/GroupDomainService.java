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

  public List<GroupMember> getGroupMembersByIdsInGroup(
      final Long groupId, final List<Long> memberIds) {
    List<GroupMember> members = groupMemberRepository.findAllByGroupIdAndIds(groupId, memberIds);
    if (members.size() != memberIds.size()) {
      throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP);
    }
    return members;
  }

  public List<Group> searchGroups(final String keyword) {
    return groupRepository.findByKeyword(keyword);
  }

  public List<GroupMember> getGroupMembers(final Long id) {
    return groupMemberRepository.findAllByGroupId(id);
  }

  public List<GroupMember> findMembersByName(final String name) {
    return groupMemberRepository.findAllByName(name);
  }

  public List<GroupMember> findMembersInGroups(final List<Long> groupIds) {
    return groupMemberRepository.findAllByGroupIds(groupIds);
  }

  public List<Group> findGroupsByIds(final List<Long> groupIds) {
    return groupRepository.findAllByIds(groupIds);
  }
}
