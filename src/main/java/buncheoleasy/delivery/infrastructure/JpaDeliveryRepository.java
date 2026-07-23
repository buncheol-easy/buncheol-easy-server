package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaDeliveryRepository extends JpaRepository<Delivery, Long> {

  Optional<Delivery> findByParticipationId(Long participationId);

  List<Delivery> findAllByParticipationIdIn(List<Long> participationIds);

  /** 참여 취소(분철 취소 cascade) 시 해당 참여의 배송 스냅샷을 정리 (bulk delete). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM Delivery d WHERE d.participationId IN :participationIds")
  int deleteByParticipationIdIn(@Param("participationIds") List<Long> participationIds);
}
