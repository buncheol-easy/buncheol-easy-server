package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ShippingFeePaybackRequest(@NotBlank String tweetUrl) {}
