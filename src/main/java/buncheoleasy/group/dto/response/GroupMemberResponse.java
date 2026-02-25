package buncheoleasy.group.dto.response;

import buncheoleasy.group.domain.member.GroupMember;

public record GroupMemberResponse(Long id, String name) {

    public static GroupMemberResponse from(GroupMember member) {
        return new GroupMemberResponse(
                member.getId(),
                member.getName()
        );
    }
}
