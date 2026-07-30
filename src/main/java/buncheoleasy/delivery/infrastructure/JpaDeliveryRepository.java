package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaDeliveryRepository extends JpaRepository<Delivery, Long> {

  Optional<Delivery> findByParticipationId(Long participationId);

  List<Delivery> findAllByParticipationIdIn(List<Long> participationIds);

  /** 운송장·배송방식이 같은 배송 조회 — 관리자 벌크 등록으로 한 운송장에 여러 배송이 매핑될 수 있어 전부 돌려준다. */
  List<Delivery> findAllByTrackingNumberAndShippingMethodAndStatusIn(
      String trackingNumber, ShippingMethod shippingMethod, Collection<DeliveryStatus> statuses);

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

  /** 운송사 추적이 지점 도착을 감지: SHIPPING → DELIVERED CAS. eventTime 은 캐리어 이벤트 시각. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Delivery d "
          + "SET d.status = :delivered, d.deliveredAt = :eventTime, d.updatedAt = :now "
          + "WHERE d.id = :id AND d.status = :shipping")
  int markDeliveredIfShipping(
      @Param("id") Long id,
      @Param("shipping") DeliveryStatus shipping,
      @Param("delivered") DeliveryStatus delivered,
      @Param("eventTime") Instant eventTime,
      @Param("now") Instant now);

  /** 운송사 추적이 고객 수령을 감지: DELIVERED → RECEIVED CAS. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Delivery d "
          + "SET d.status = :received, d.receivedAt = :eventTime, d.updatedAt = :now "
          + "WHERE d.id = :id AND d.status = :delivered")
  int markReceivedIfDelivered(
      @Param("id") Long id,
      @Param("delivered") DeliveryStatus delivered,
      @Param("received") DeliveryStatus received,
      @Param("eventTime") Instant eventTime,
      @Param("now") Instant now);

  /**
   * 도착 콜백을 놓친 채 고객 수령이 감지된 직행 전이: SHIPPING → RECEIVED CAS. 탈퇴 가드·환급 기산이 deliveredAt 을 참조하므로 null 로
   * 남기지 않고 수령 시각으로 함께 채운다(실제 지점 도착 시각은 알 수 없어 근사치).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Delivery d "
          + "SET d.status = :received, d.deliveredAt = :eventTime, d.receivedAt = :eventTime, "
          + "    d.updatedAt = :now "
          + "WHERE d.id = :id AND d.status = :shipping")
  int markReceivedIfShipping(
      @Param("id") Long id,
      @Param("shipping") DeliveryStatus shipping,
      @Param("received") DeliveryStatus received,
      @Param("eventTime") Instant eventTime,
      @Param("now") Instant now);
}
