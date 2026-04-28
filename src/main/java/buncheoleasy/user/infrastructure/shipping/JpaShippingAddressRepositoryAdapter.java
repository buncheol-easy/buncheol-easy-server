package buncheoleasy.user.infrastructure.shipping;

import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressRepository;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaShippingAddressRepositoryAdapter implements ShippingAddressRepository {

  private final JpaShippingAddressRepository jpaShippingAddressRepository;

  @Override
  public ShippingAddress save(ShippingAddress shippingAddress) {
    return jpaShippingAddressRepository.save(shippingAddress);
  }

  @Override
  public void delete(Long id) {
    jpaShippingAddressRepository.deleteById(id);
  }

  @Override
  public Optional<ShippingAddress> findById(Long id) {
    return jpaShippingAddressRepository.findById(id);
  }

  @Override
  public List<ShippingAddress> getUserShippingAddresses(Long userId) {
    return jpaShippingAddressRepository.findAllByUserId(userId);
  }

  @Override
  public int countByUserId(Long userId) {
    return jpaShippingAddressRepository.countByUserId(userId);
  }

  @Override
  public boolean existsByUserIdAndShippingMethodAndStoreName(
      Long userId, String shippingMethod, String storeName) {
    return jpaShippingAddressRepository.existsByUserIdAndShippingMethodAndStoreName(
        userId, ShippingMethod.of(shippingMethod), storeName);
  }
}
