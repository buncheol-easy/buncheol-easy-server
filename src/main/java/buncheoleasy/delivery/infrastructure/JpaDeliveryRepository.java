package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.time.Instant;
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

  /**
   * 운송장 등록 CAS. SNAPSHOTTED → SHIPPING 최초 등록과 SHIPPING 상태의 번호 재등록(last-write-wins)을 모두 허용한다.
   * 배송이 이미 DELIVERED/RECEIVED 로 진행됐으면 0 을 반환해 역행을 막는다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Delivery d "
          + "SET d.status = :shipping, d.trackingNumber = :trackingNumber, "
          + "    d.trackingRegisteredAt = :now, d.updatedAt = :now "
          + "WHERE d.id = :id AND d.status IN (:snapshotted, :shipping)")
  int registerTrackingIfRegistrable(
      @Param("id") Long id,
      @Param("trackingNumber") String trackingNumber,
      @Param("snapshotted") DeliveryStatus snapshotted,
      @Param("shipping") DeliveryStatus shipping,
      @Param("now") Instant now);

  /** 수령 확인 CAS (SHIPPING·DELIVERED → RECEIVED). deliveredAt 은 건드리지 않는다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Delivery d "
          + "SET d.status = :received, d.receivedAt = :now, d.updatedAt = :now "
          + "WHERE d.id = :id AND d.status IN (:shipping, :delivered)")
  int confirmReceiptIfActive(
      @Param("id") Long id,
      @Param("shipping") DeliveryStatus shipping,
      @Param("delivered") DeliveryStatus delivered,
      @Param("received") DeliveryStatus received,
      @Param("now") Instant now);
}
