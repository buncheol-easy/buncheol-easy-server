package buncheoleasy.group.application;

import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.dto.response.GroupMemberResponse;
import buncheoleasy.group.dto.response.GroupResponse;
import buncheoleasy.group.dto.response.GroupWithMembersResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

  private final GroupDomainService groupDomainService;

  @Transactional(readOnly = true)
  public List<GroupResponse> searchGroups(final String keyword) {
    return groupDomainService.searchGroups(keyword).stream().map(GroupResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<GroupMemberResponse> getGroupMembers(final Long groupId) {
    groupDomainService.validateGroupExists(groupId);
    return groupDomainService.getGroupMembers(groupId).stream()
        .map(GroupMemberResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<GroupWithMembersResponse> searchGroupsByMemberName(final String keyword) {
    List<GroupMember> matched = groupDomainService.findMembersByName(keyword);
    if (matched.isEmpty()) {
      return List.of();
    }

    List<Long> groupIds = matched.stream().map(GroupMember::getGroupId).distinct().toList();

    Map<Long, List<GroupMemberResponse>> membersByGroupId =
        groupDomainService.findMembersInGroups(groupIds).stream()
            .collect(
                Collectors.groupingBy(
                    GroupMember::getGroupId,
                    Collectors.mapping(GroupMemberResponse::from, Collectors.toList())));

    return groupDomainService.findGroupsByIds(groupIds).stream()
        .map(group -> toGroupWithMembers(group, membersByGroupId))
        .toList();
  }

  private GroupWithMembersResponse toGroupWithMembers(
      final Group group, final Map<Long, List<GroupMemberResponse>> membersByGroupId) {
    return GroupWithMembersResponse.of(
        group, membersByGroupId.getOrDefault(group.getId(), List.of()));
  }
}
