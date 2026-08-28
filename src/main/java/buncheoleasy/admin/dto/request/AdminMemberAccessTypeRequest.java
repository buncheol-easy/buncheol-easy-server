package buncheoleasy.admin.dto.request;

import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import jakarta.validation.constraints.NotNull;

public record AdminMemberAccessTypeRequest(@NotNull BuncheolMemberAccessType accessType) {}
