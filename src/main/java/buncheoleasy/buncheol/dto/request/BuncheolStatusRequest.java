package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import jakarta.validation.constraints.NotNull;

public record BuncheolStatusRequest(@NotNull BuncheolStatus status) {}
