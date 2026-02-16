package buncheoleasy.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateShippingAddressRequest(
        @NotBlank(message = "배송 방법은 필수입니다.")
        @Pattern(regexp = "^(GS25_HALF|CU_HALF)$", message = "유효하지 않은 배송 방법입니다.")
        String shippingMethod,

        @NotBlank(message = "지점명은 필수입니다.")
        String storeName
) {
}
