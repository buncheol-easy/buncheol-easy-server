package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateBidRequest(@NotNull Long shippingAddressId, @Positive long bidAmount) {}
