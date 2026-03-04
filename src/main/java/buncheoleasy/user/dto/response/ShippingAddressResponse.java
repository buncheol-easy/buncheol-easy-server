package buncheoleasy.user.dto.response;

public record ShippingAddressResponse(Long id, String shippingMethod, String storeName) {
  public static ShippingAddressResponse of(
      final Long id, final String shippingMethod, final String storeName) {
    return new ShippingAddressResponse(id, shippingMethod, storeName);
  }
}
