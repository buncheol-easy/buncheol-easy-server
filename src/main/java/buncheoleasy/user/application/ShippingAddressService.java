package buncheoleasy.user.application;

import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.dto.request.ShippingAddressRequest;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

    private final ShippingAddressDomainService shippingAddressDomainService;

    public void registerShippingAddress(final Long userId, final ShippingAddressRequest request) {
        shippingAddressDomainService.createShippingAddress(userId, request.shippingMethod(), request.storeName());
    }

    public void modifyShippingAddress(final Long userId, final Long addressId, final ShippingAddressRequest request) {
        shippingAddressDomainService.updateShippingAddress(userId, addressId, request.shippingMethod(),
                request.storeName());
    }

    public void removeShippingAddress(final Long userId, final Long addressId) {
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
