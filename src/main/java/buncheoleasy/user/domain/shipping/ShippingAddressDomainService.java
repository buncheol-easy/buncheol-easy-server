package buncheoleasy.user.domain.shipping;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingAddressDomainService {

  private static final int MAX_SHIPPING_ADDRESS_COUNT = 5;

  private final ShippingAddressRepository shippingAddressRepository;

  /** 점포 코드 없는 등록 — 접수처 검색 이전 클라이언트와의 하위 호환용. */
  @Transactional
  public ShippingAddress createShippingAddress(
      final Long userId,
      final String shippingMethod,
      final String storeName,
      final String alias,
      final boolean isDefault) {
    return createShippingAddress(userId, shippingMethod, storeName, null, alias, isDefault);
  }

  @Transactional
  public ShippingAddress createShippingAddress(
      final Long userId,
      final String shippingMethod,
      final String storeName,
      final String storeCode,
      final String alias,
      final boolean isDefault) {
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

    if (isDefault) {
      shippingAddressRepository.clearDefaultByUserAndMethod(userId, shippingMethod, null);
    }

    return shippingAddressRepository.save(
        ShippingAddress.create(userId, shippingMethod, storeName, storeCode, alias, isDefault));
  }

  /** 점포 코드 없는 수정 — 하위 호환용. 코드 유지/초기화 규칙은 {@link ShippingAddress#update} 를 따른다. */
  @Transactional
  public void updateShippingAddress(
      final Long userId,
      final Long id,
      final String shippingMethod,
      final String storeName,
      final String alias,
      final boolean isDefault) {
    updateShippingAddress(userId, id, shippingMethod, storeName, null, alias, isDefault);
  }

  @Transactional
  public void updateShippingAddress(
      final Long userId,
      final Long id,
      final String shippingMethod,
      final String storeName,
      final String storeCode,
      final String alias,
      final boolean isDefault) {
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

    if (isDefault) {
      shippingAddressRepository.clearDefaultByUserAndMethod(userId, shippingMethod, id);
    }

    shippingAddress.update(shippingMethod, storeName, storeCode, alias, isDefault);
  }

  public void validateOwnership(final Long userId, final Long id) {
    ShippingAddress shippingAddress = getShippingAddress(id);
    if (!shippingAddress.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
    }
  }

  public void deleteShippingAddress(final Long userId, final Long id) {
    validateOwnership(userId, id);
    shippingAddressRepository.delete(id);
  }

  public List<ShippingAddress> getUserShippingAddresses(final Long userId) {
    return shippingAddressRepository.getUserShippingAddresses(userId);
  }

  public ShippingAddress getShippingAddress(final Long id) {
    return shippingAddressRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND));
  }
}
