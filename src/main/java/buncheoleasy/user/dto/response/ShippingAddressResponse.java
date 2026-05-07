package buncheoleasy.user.dto.response;

public record ShippingAddressResponse(
    Long id, String shippingMethod, String storeName, String alias, boolean isDefault) {
  public static ShippingAddressResponse of(
      final Long id,
      final String shippingMethod,
      final String storeName,
      final String alias,
      final boolean isDefault) {
    return new ShippingAddressResponse(id, shippingMethod, storeName, alias, isDefault);
  }
}
