package buncheoleasy.user.dto.response;

public record ShippingAddressResponse(
        String shippingMethod,
        String storeName
) {
    public static ShippingAddressResponse of(final String shippingMethod, final String storeName) {
        return new ShippingAddressResponse(shippingMethod, storeName);
    }
}
