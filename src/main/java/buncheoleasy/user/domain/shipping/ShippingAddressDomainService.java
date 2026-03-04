package buncheoleasy.user.domain.shipping;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShippingAddressDomainService {

  private static final int MAX_SHIPPING_ADDRESS_COUNT = 10;

  private final ShippingAddressRepository shippingAddressRepository;

  public ShippingAddress createShippingAddress(
      final Long userId, final String shippingMethod, final String storeName) {
    // 개수 제한 체크
    int currentCount = shippingAddressRepository.countByUserId(userId);
    if (currentCount >= MAX_SHIPPING_ADDRESS_COUNT) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED);
    }

    // 중복 체크
    if (shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
        userId, shippingMethod, storeName)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_DUPLICATE);
    }

    ShippingAddress shippingAddress = ShippingAddress.create(userId, shippingMethod, storeName);
    return shippingAddressRepository.save(shippingAddress);
  }

  public void updateShippingAddress(
      final Long userId, final Long id, final String shippingMethod, final String storeName) {
    ShippingAddress shippingAddress = getShippingAddress(id);

    if (!shippingAddress.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
    }

    // 중복 체크 (변경 사항이 있고, 중복되는 경우만)
    if (!shippingAddress.isSameAddress(shippingMethod, storeName)
        && shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
            userId, shippingMethod, storeName)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_DUPLICATE);
    }

    shippingAddress.update(shippingMethod, storeName);
    shippingAddressRepository.update(shippingAddress);
  }

  public void deleteShippingAddress(final Long userId, final Long id) {
    ShippingAddress shippingAddress = getShippingAddress(id);

    if (!shippingAddress.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
    }

    shippingAddressRepository.delete(id);
  }

  public List<ShippingAddress> getUserShippingAddresses(final Long userId) {
    return shippingAddressRepository.getUserShippingAddresses(userId);
  }

  private ShippingAddress getShippingAddress(final Long id) {
    return shippingAddressRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND));
  }
}
