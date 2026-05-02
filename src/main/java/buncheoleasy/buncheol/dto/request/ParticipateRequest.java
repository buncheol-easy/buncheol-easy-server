package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ParticipateRequest(
    @NotNull Long buncheolMemberId,
    @NotNull Long shippingAddressId,
    @NotNull @Positive Long bidAmount) {}
