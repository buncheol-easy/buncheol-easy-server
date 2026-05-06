package buncheoleasy.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
    @NotBlank String shippingMethod,
    @NotBlank @Size(min = 1, max = 100) String storeName,
    @Size(max = 10) String alias,
    Boolean isDefault) {

  public ShippingAddressRequest {
    isDefault = isDefault != null && isDefault;
  }
}
