package buncheoleasy.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank
        @Size(min = 1, max = 20)
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$")
        String nickname,

        @NotBlank
        @Size(min = 10, max = 11)
        @Pattern(regexp = "^01[0-9]+$")
        String phoneNumber
) {
}
