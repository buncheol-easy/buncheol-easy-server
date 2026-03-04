package buncheoleasy.group.application;

import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.dto.response.GroupMemberResponse;
import buncheoleasy.group.dto.response.GroupResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupService {

  private final GroupDomainService groupDomainService;

  public List<GroupResponse> searchGroups(final String keyword) {
    return groupDomainService.searchGroups(keyword).stream().map(GroupResponse::from).toList();
  }

  public List<GroupMemberResponse> getGroupMembers(final Long groupId) {
    groupDomainService.validateGroupExists(groupId);
    return groupDomainService.getGroupMembers(groupId).stream()
        .map(GroupMemberResponse::from)
        .toList();
  }
}
