package buncheoleasy.user.domain.shipping;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class ShippingAddressDomainService {

    private static final int MAX_SHIPPING_ADDRESS_COUNT = 10;

    private final ShippingAddressRepository shippingAddressRepository;

    @Transactional
    public ShippingAddress createShippingAddress(final Long userId, final String shippingMethod,
                                                 final String storeName) {
        // 개수 제한 체크
        int currentCount = shippingAddressRepository.countByUserId(userId);
        if (currentCount >= MAX_SHIPPING_ADDRESS_COUNT) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED);
        }

        // 중복 체크
        if (shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(userId, shippingMethod,
                storeName)) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_DUPLICATE);
        }

        ShippingAddress shippingAddress = ShippingAddress.create(userId, shippingMethod, storeName);
        return shippingAddressRepository.save(shippingAddress);
    }

    @Transactional
    public void updateShippingAddress(final Long userId, final Long addressId, final String shippingMethod,
                                      final String storeName) {
        ShippingAddress shippingAddress = shippingAddressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND));

        // 본인 소유 확인
        if (!shippingAddress.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
        }

        // 중복 체크 (변경 사항이 있고, 중복되는 경우만)
        if (!shippingAddress.isSameAddress(shippingMethod, storeName) &&
                shippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(userId, shippingMethod,
                        storeName)) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_DUPLICATE);
        }

        shippingAddress.update(shippingMethod, storeName);
        shippingAddressRepository.update(shippingAddress);
    }

    @Transactional
    public void deleteShippingAddress(final Long userId, final Long addressId) {
        ShippingAddress shippingAddress = shippingAddressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND));

        // 본인 소유 확인
        if (!shippingAddress.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
        }

        shippingAddressRepository.delete(addressId);
    }

    public List<ShippingAddress> getUserShippingAddresses(final Long userId) {
        return shippingAddressRepository.getUserShippingAddresses(userId);
    }
}
