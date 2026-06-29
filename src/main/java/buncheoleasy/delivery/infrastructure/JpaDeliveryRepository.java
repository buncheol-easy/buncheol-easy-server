package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaDeliveryRepository extends JpaRepository<Delivery, Long> {

  Optional<Delivery> findByParticipationId(Long participationId);

  List<Delivery> findAllByParticipationIdIn(List<Long> participationIds);
}
