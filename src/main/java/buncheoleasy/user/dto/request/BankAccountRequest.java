package buncheoleasy.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BankAccountRequest(
    @NotBlank @Size(max = 50) String bank,
    @NotBlank @Size(max = 50) @Pattern(regexp = "^\\d+(-\\d+)*$") String account,
    @NotBlank @Size(max = 50) String holder) {}
