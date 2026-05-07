package buncheoleasy.user.domain.shipping;

import java.util.List;
import java.util.Optional;

public interface ShippingAddressRepository {

  ShippingAddress save(ShippingAddress shippingAddress);

  void delete(Long id);

  Optional<ShippingAddress> findById(Long id);

  List<ShippingAddress> getUserShippingAddresses(Long userId);

  int countByUserId(Long userId);

  boolean existsByUserIdAndShippingMethodAndStoreName(
      Long userId, String shippingMethod, String storeName);

  /** 같은 (user, method) 의 다른 기본 배송지를 일괄 false 처리. excludeId가 null이면 전체 대상. */
  void clearDefaultByUserAndMethod(Long userId, String shippingMethod, Long excludeId);
}
