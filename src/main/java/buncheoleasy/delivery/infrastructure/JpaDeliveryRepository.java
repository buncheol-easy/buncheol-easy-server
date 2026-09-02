package buncheoleasy.delivery.infrastructure;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaDeliveryRepository extends JpaRepository<Delivery, Long> {

  /** 묶음의 배송. 전환 이전 다슬롯 묶음은 여러 건일 수 있어 id 최소값 1건으로 확정한다 (포트 javadoc). */
  Optional<Delivery> findFirstByBundleIdOrderByIdAsc(Long bundleId);

  /** id 오름차순 — 호출부가 앞선 것을 이기게(merge (a, b) -> a) 두면 위와 같은 규칙이 된다. */
  List<Delivery> findAllByBundleIdInOrderByIdAsc(List<Long> bundleIds);

  /** 운송장·배송방식이 같은 배송 조회 — 관리자 벌크 등록으로 한 운송장에 여러 배송이 매핑될 수 있어 전부 돌려준다. */
  List<Delivery> findAllByTrackingNumberAndShippingMethodAndStatusIn(
      String trackingNumber, ShippingMethod shippingMethod, Collection<DeliveryStatus> statuses);

  /** 지점 도착 후 미수령 독촉 대상 조회 — 도착이 오래된 순. */
  List<Delivery>
      findByStatusAndDeliveredAtLessThanEqualAndPickupReminderSentAtIsNullOrderByDeliveredAtAsc(
          DeliveryStatus status, Instant threshold, Limit limit);

  /** 미수령 독촉 1회 발송 마킹 CAS. {@code pickupReminderSentAt IS NULL} 조건이 중복 발송을 막는 dedup 가드다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Delivery d "
          + "SET d.pickupReminderSentAt = :now, d.updatedAt = :now "
          + "WHERE d.id = :id AND d.status = :delivered AND d.pickupReminderSentAt IS NULL")
  int markPickupReminderSentIfDue(
      @Param("id") Long id,
      @Param("delivered") DeliveryStatus delivered,
      @Param("now") Instant now);

  /**
   * 추적 중 운송장을 배송방식·번호 단위로 중복 제거해 조회 — 상태 변경이 오래된(정체된) 운송장 우선. 등록이 {@code registeredAfter} 이전인
   * 운송장은 추적 포기로 제외한다 — 전이가 영영 안 되는 건(반송·오타)이 정렬 앞자리를 영구 점유해 신규 운송장을 굶기는 것을 막는다.
   */
  @Query(
      "SELECT new buncheoleasy.delivery.domain.TrackedParcel(d.shippingMethod, d.trackingNumber) "
          + "FROM Delivery d "
          + "WHERE d.status IN :statuses AND d.trackingNumber IS NOT NULL "
          + "AND d.trackingRegisteredAt > :registeredAfter "
          + "GROUP BY d.shippingMethod, d.trackingNumber "
          + "ORDER BY MIN(d.updatedAt) ASC")
  List<TrackedParcel> findTrackedParcels(
      @Param("statuses") Collection<DeliveryStatus> statuses,
      @Param("registeredAfter") Instant registeredAfter,
      Limit limit);

  /** 참여 취소(분철 취소 cascade) 시 해당 참여의 배송 스냅샷을 정리 (bulk delete). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM Delivery d WHERE d.participationId IN :participationIds")
  int deleteByParticipationIdIn(@Param("participationIds") List<Long> participationIds);

  /** 운송장 등록 CAS — 최초 등록과 SHIPPING 재등록(번호 last-write-wins) 허용, DELIVERED/RECEIVED 역행 차단. */
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

  /** 지점 도착 감지 CAS (SHIPPING → DELIVERED). eventTime 은 캐리어 이벤트 시각. */
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

  /** 고객 수령 감지 CAS (DELIVERED → RECEIVED). */
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
   * 도착 감지를 놓친 직행 CAS (SHIPPING → RECEIVED). 탈퇴 가드·환급 기산이 참조하는 deliveredAt 을 수령 시각으로 함께 채운다(근사치).
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
