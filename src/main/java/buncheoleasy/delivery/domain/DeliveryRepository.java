package buncheoleasy.delivery.domain;

import java.util.Optional;

public interface DeliveryRepository {

  Delivery save(Delivery delivery);

  Optional<Delivery> findById(Long id);

  Optional<Delivery> findByParticipationId(Long participationId);

  boolean updateStatus(Delivery delivery, DeliveryStatus expectedStatus);
}
