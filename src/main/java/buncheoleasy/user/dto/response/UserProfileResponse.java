package buncheoleasy.user.dto.response;

import java.util.List;

public record UserProfileResponse(
        String provider,
        String email,
        String nickname,
        String phoneNumber,
        List<ShippingAddressResponse> shippingAddresses
) {
    public static UserProfileResponse of(final String provider, final String email, final String nickname,
                                         final String phoneNumber,
                                         final List<ShippingAddressResponse> shippingAddresses) {
        return new UserProfileResponse(provider, email, nickname, phoneNumber, shippingAddresses);
    }
}
