package buncheoleasy.user.domain.shipping;

import java.util.List;
import java.util.Optional;

public interface ShippingAddressRepository {

  ShippingAddress save(ShippingAddress shippingAddress);

  void delete(Long id);

  Optional<ShippingAddress> findById(Long id);

  /** 여러 배송지를 id 로 일괄 조회. 참여 목록의 배송비 산정처럼 배송지 다건이 필요한 조회에서 N+1 을 피하기 위해 사용. */
  List<ShippingAddress> findAllByIds(List<Long> ids);

  List<ShippingAddress> getUserShippingAddresses(Long userId);

  int countByUserId(Long userId);

  boolean existsByUserIdAndShippingMethodAndStoreName(
      Long userId, String shippingMethod, String storeName);

  /** 접수처 점포 코드가 연결된 배송지 전체 — 지점명 정합성 배치가 마스터와 대조할 대상. */
  List<ShippingAddress> findAllByStoreCodeIsNotNull();

  /** 같은 (user, method) 의 다른 기본 배송지를 일괄 false 처리. excludeId가 null이면 전체 대상. */
  void clearDefaultByUserAndMethod(Long userId, String shippingMethod, Long excludeId);
}
