package buncheoleasy.user.application;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.dto.request.ShippingAddressRequest;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

  private final ShippingAddressDomainService shippingAddressDomainService;
  private final UserDomainService userDomainService;

  @Transactional
  public void registerShippingAddress(final Long userId, final ShippingAddressRequest request) {
    validateUser(userId);
    shippingAddressDomainService.createShippingAddress(
        userId,
        request.shippingMethod(),
        request.storeName(),
        request.alias(),
        request.isDefault());
  }

  public void modifyShippingAddress(
      final Long userId, final Long addressId, final ShippingAddressRequest request) {
    validateUser(userId);
    shippingAddressDomainService.updateShippingAddress(
        userId,
        addressId,
        request.shippingMethod(),
        request.storeName(),
        request.alias(),
        request.isDefault());
  }

  public void removeShippingAddress(final Long userId, final Long addressId) {
    validateUser(userId);
    shippingAddressDomainService.deleteShippingAddress(userId, addressId);
  }

  public List<ShippingAddressResponse> getUserShippingAddresses(final Long userId) {
    validateUser(userId);
    return shippingAddressDomainService.getUserShippingAddresses(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  private void validateUser(final Long userId) {
    if (!userDomainService.isValidUser(userId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
  }

  private ShippingAddressResponse toResponse(final ShippingAddress shippingAddress) {
    return ShippingAddressResponse.of(
        shippingAddress.getId(),
        shippingAddress.getShippingMethod().name(),
        shippingAddress.getStoreName(),
        shippingAddress.getAlias(),
        shippingAddress.isDefault());
  }
}
