package buncheoleasy.group.dto.response;

import buncheoleasy.group.domain.Group;

public record GroupResponse(Long id, String name) {

    public static GroupResponse from(Group group) {
        return new GroupResponse(group.getId(), group.getName());
    }
}
