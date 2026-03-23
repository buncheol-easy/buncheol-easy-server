package buncheoleasy.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TrackingRegistrationRequest(@NotBlank String trackingNumber) {}
