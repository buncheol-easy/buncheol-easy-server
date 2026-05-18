package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDeliveryRepositoryAdapter implements DeliveryRepository {

  private final JpaDeliveryRepository jpaDeliveryRepository;
  private final Clock clock;

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
  public boolean updateStatus(final Delivery delivery, final DeliveryStatus expectedStatus) {
    int updated =
        jpaDeliveryRepository.updateStatusIfMatches(
            delivery.getId(),
            delivery.getTrackingNumber(),
            delivery.getTrackingRegisteredAt(),
            delivery.getDeliveredAt(),
            delivery.getReceivedAt(),
            delivery.getStatus(),
            Instant.now(clock),
            expectedStatus);
    return updated > 0;
  }
}
