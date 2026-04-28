package buncheoleasy.user.infrastructure.shipping;

import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {

  List<ShippingAddress> findAllByUserId(Long userId);

  int countByUserId(Long userId);

  boolean existsByUserIdAndShippingMethodAndStoreName(
      Long userId, ShippingMethod shippingMethod, String storeName);
}
