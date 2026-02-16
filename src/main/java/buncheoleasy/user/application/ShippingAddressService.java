package buncheoleasy.user.application;

import buncheoleasy.user.domain.ShippingAddress;
import buncheoleasy.user.domain.ShippingAddressDomainService;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

    private final ShippingAddressDomainService shippingAddressDomainService;

    public void createShippingAddress(final Long userId, final String shippingMethod, final String storeName) {
        shippingAddressDomainService.createShippingAddress(userId, shippingMethod, storeName);
    }

    public void updateShippingAddress(final Long userId, final Long addressId, final String shippingMethod,
                                       final String storeName) {
        shippingAddressDomainService.updateShippingAddress(userId, addressId, shippingMethod, storeName);
    }

    public void deleteShippingAddress(final Long userId, final Long addressId) {
        shippingAddressDomainService.deleteShippingAddress(userId, addressId);
    }

    public List<ShippingAddressResponse> getUserShippingAddresses(final Long userId) {
        return shippingAddressDomainService.getUserShippingAddresses(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ShippingAddressResponse toResponse(final ShippingAddress shippingAddress) {
        return ShippingAddressResponse.of(
                shippingAddress.getId(),
                shippingAddress.getShippingMethod().name(),
                shippingAddress.getStoreName()
        );
    }
}
