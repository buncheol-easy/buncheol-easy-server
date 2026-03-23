package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisDeliveryRepository implements DeliveryRepository {

  private final DeliveryMapper deliveryMapper;

  @Override
  public Delivery save(final Delivery delivery) {
    deliveryMapper.insert(delivery);
    return delivery;
  }

  @Override
  public Optional<Delivery> findById(final Long id) {
    return deliveryMapper.findById(id);
  }

  @Override
  public Optional<Delivery> findByParticipationId(final Long participationId) {
    return deliveryMapper.findByParticipationId(participationId);
  }

  @Override
  public boolean updateStatus(final Delivery delivery, final DeliveryStatus expectedStatus) {
    return deliveryMapper.updateStatus(delivery, expectedStatus) > 0;
  }
}
