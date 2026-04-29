package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BuncheolMemberRequest(
    Long buncheolMemberId, @NotNull Long memberId, @NotNull @Positive Long bidMinPrice) {}
