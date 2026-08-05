package buncheoleasy.user.infrastructure.shipping;

import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {

  List<ShippingAddress> findAllByUserId(Long userId);

  int countByUserId(Long userId);

  boolean existsByUserIdAndShippingMethodAndStoreName(
      Long userId, ShippingMethod shippingMethod, String storeName);

  List<ShippingAddress> findAllByStoreCodeIsNotNull();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update ShippingAddress sa set sa.isDefault = false "
          + "where sa.userId = :userId "
          + "and sa.shippingMethod = :shippingMethod "
          + "and sa.isDefault = true "
          + "and (:excludeId is null or sa.id <> :excludeId)")
  int clearDefaultByUserAndMethod(
      @Param("userId") Long userId,
      @Param("shippingMethod") ShippingMethod shippingMethod,
      @Param("excludeId") Long excludeId);
}
