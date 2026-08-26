package buncheoleasy.admin.dto.request;

import buncheoleasy.buncheol.domain.member.SlotAccessType;
import jakarta.validation.constraints.NotNull;

public record AdminSlotAccessTypeRequest(@NotNull SlotAccessType accessType) {}
