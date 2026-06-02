package buncheoleasy.delivery.domain;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository {

  Delivery save(Delivery delivery);

  Optional<Delivery> findById(Long id);

  Optional<Delivery> findByParticipationId(Long participationId);

  List<Delivery> findAllByParticipationIds(List<Long> participationIds);

  boolean updateStatus(Delivery delivery, DeliveryStatus expectedStatus);
}
