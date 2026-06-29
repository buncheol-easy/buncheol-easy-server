package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDeliveryRepositoryAdapter implements DeliveryRepository {

  private final JpaDeliveryRepository jpaDeliveryRepository;

  @Override
  public Delivery save(final Delivery delivery) {
    return jpaDeliveryRepository.save(delivery);
  }

  @Override
  public Optional<Delivery> findById(final Long id) {
    return jpaDeliveryRepository.findById(id);
  }

  @Override
  public Optional<Delivery> findByParticipationId(final Long participationId) {
    return jpaDeliveryRepository.findByParticipationId(participationId);
  }

  @Override
  public List<Delivery> findAllByParticipationIds(final List<Long> participationIds) {
    if (participationIds.isEmpty()) {
      return List.of();
    }
    return jpaDeliveryRepository.findAllByParticipationIdIn(participationIds);
  }
}
