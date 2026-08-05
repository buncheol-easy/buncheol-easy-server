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

  /** 테스트 편의용 — JSON 역직렬화는 canonical 생성자를 쓰므로 구 형식 하위 호환은 storeCode nullable 만으로 이미 성립한다. */
  public ShippingAddressRequest(
      final String shippingMethod,
      final String storeName,
      final String alias,
      final Boolean isDefault) {
    this(shippingMethod, storeName, null, alias, isDefault);
  }
}
