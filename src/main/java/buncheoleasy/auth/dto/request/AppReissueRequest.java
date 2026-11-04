package buncheoleasy.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppReissueRequest(@NotBlank @Size(max = 2048) String refreshToken) {}
