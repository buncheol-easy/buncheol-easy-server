package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaDeliveryRepository extends JpaRepository<Delivery, Long> {

  Optional<Delivery> findByParticipationId(Long participationId);

  /**
   * status 가 expectedStatus 인 경우에만 한 행을 갱신한다 (compare-and-swap). bulk JPQL UPDATE는 영속성 컨텍스트를 우회하므로
   * clearAutomatically=true로 캐시 동기화를 보장한다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Delivery d "
          + "SET d.trackingNumber = :trackingNumber, "
          + "    d.trackingRegisteredAt = :trackingRegisteredAt, "
          + "    d.deliveredAt = :deliveredAt, "
          + "    d.receivedAt = :receivedAt, "
          + "    d.status = :newStatus, "
          + "    d.updatedAt = :now "
          + "WHERE d.id = :id AND d.status = :expectedStatus")
  int updateStatusIfMatches(
      @Param("id") Long id,
      @Param("trackingNumber") String trackingNumber,
      @Param("trackingRegisteredAt") LocalDateTime trackingRegisteredAt,
      @Param("deliveredAt") LocalDateTime deliveredAt,
      @Param("receivedAt") LocalDateTime receivedAt,
      @Param("newStatus") DeliveryStatus newStatus,
      @Param("now") LocalDateTime now,
      @Param("expectedStatus") DeliveryStatus expectedStatus);
}
