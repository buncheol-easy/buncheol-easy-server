package buncheoleasy.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
    @NotBlank String shippingMethod,
    @NotBlank @Size(min = 1, max = 100) String storeName,
    @Size(max = 20) String storeCode,
    @Size(max = 10) String alias,
    Boolean isDefault) {

  public ShippingAddressRequest {
    isDefault = isDefault != null && isDefault;
  }

  /** 점포 코드 없는 생성 — 접수처 검색 이전 형식과의 하위 호환용. */
  public ShippingAddressRequest(
      final String shippingMethod,
      final String storeName,
      final String alias,
      final Boolean isDefault) {
    this(shippingMethod, storeName, null, alias, isDefault);
  }
}
