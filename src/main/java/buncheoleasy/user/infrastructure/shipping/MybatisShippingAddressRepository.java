package buncheoleasy.user.infrastructure.shipping;

import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class MybatisShippingAddressRepository implements ShippingAddressRepository {

  private final ShippingAddressMapper shippingAddressMapper;

  @Override
  public ShippingAddress save(ShippingAddress shippingAddress) {
    shippingAddressMapper.insert(shippingAddress);
    return shippingAddress;
  }

  @Override
  public void update(ShippingAddress shippingAddress) {
    shippingAddressMapper.update(shippingAddress);
  }

  @Override
  public void delete(Long id) {
    shippingAddressMapper.delete(id);
  }

  @Override
  public Optional<ShippingAddress> findById(Long id) {
    return shippingAddressMapper.findById(id);
  }

  @Override
  public List<ShippingAddress> getUserShippingAddresses(Long userId) {
    return shippingAddressMapper.findAllByUser(userId);
  }

  @Override
  public int countByUserId(Long userId) {
    return shippingAddressMapper.countByUserId(userId);
  }

  @Override
  public boolean existsByUserIdAndShippingMethodAndStoreName(
      Long userId, String shippingMethod, String storeName) {
    return shippingAddressMapper.existsByUserIdAndShippingMethodAndStoreName(
        userId, shippingMethod, storeName);
  }
}
