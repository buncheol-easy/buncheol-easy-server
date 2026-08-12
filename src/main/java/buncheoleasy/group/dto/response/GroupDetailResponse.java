package buncheoleasy.group.dto.response;

import buncheoleasy.group.domain.Group;
import java.util.List;

/** 그룹 단위 브라우즈(아티스트 페이지) 헤더 응답. 멤버 필터 칩과 "모집중 N개" 표기에 필요한 값을 한 번에 내려준다. */
public record GroupDetailResponse(
    Long id,
    String name,
    String image,
    List<GroupMemberResponse> members,
    long recruitingBuncheolCount) {

  public static GroupDetailResponse of(
      final Group group,
      final List<GroupMemberResponse> members,
      final long recruitingBuncheolCount) {
    return new GroupDetailResponse(
        group.getId(), group.getName(), group.getImage(), members, recruitingBuncheolCount);
  }
}
