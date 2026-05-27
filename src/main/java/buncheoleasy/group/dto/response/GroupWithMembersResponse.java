package buncheoleasy.group.dto.response;

import buncheoleasy.group.domain.Group;
import java.util.List;

public record GroupWithMembersResponse(
    Long id, String name, String image, List<GroupMemberResponse> members) {

  public static GroupWithMembersResponse of(
      final Group group, final List<GroupMemberResponse> members) {
    return new GroupWithMembersResponse(group.getId(), group.getName(), group.getImage(), members);
  }
}
