package buncheoleasy.user.domain.shipping;

import java.util.List;
import java.util.Optional;

public interface ShippingAddressRepository {

  ShippingAddress save(ShippingAddress shippingAddress);

  void update(ShippingAddress shippingAddress);

  void delete(Long id);

  Optional<ShippingAddress> findById(Long id);

  List<ShippingAddress> getUserShippingAddresses(Long userId);

  int countByUserId(Long userId);

  boolean existsByUserIdAndShippingMethodAndStoreName(
      Long userId, String shippingMethod, String storeName);
}
