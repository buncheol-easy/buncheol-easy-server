package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeliveryMapper {

  void insert(Delivery delivery);

  Optional<Delivery> findById(Long id);

  Optional<Delivery> findByParticipationId(Long participationId);

  int updateStatus(
      @Param("delivery") Delivery delivery, @Param("expectedStatus") DeliveryStatus expectedStatus);
}
