package buncheoleasy.delivery.domain;

import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository {

  Delivery save(Delivery delivery);

  /** 운송장 등록 CAS (SNAPSHOTTED·SHIPPING → SHIPPING). 호출 측 {@code @Transactional} 필수. */
  boolean registerTrackingIfRegistrable(Long id, String trackingNumber, Instant now);

  /** 수령 확인 CAS (SHIPPING·DELIVERED → RECEIVED). 호출 측 {@code @Transactional} 필수. */
  boolean confirmReceiptIfActive(Long id, Instant now);

  /** 지점 도착 감지 CAS (SHIPPING → DELIVERED). 호출 측 {@code @Transactional} 필수. */
  boolean markDeliveredIfShipping(Long id, Instant eventTime, Instant now);

  /** 고객 수령 감지 CAS (DELIVERED → RECEIVED). 호출 측 {@code @Transactional} 필수. */
  boolean markReceivedIfDelivered(Long id, Instant eventTime, Instant now);

  /** 도착 감지를 놓친 직행 CAS (SHIPPING → RECEIVED, deliveredAt 도 채움). 호출 측 {@code @Transactional} 필수. */
  boolean markReceivedIfShipping(Long id, Instant eventTime, Instant now);

  /** 운송장·배송방식이 같은 지정 상태의 배송 전부 조회 — 관리자 벌크 등록으로 한 운송장에 여러 배송이 매핑될 수 있다. */
  List<Delivery> findAllByTrackingNumber(
      String trackingNumber, ShippingMethod shippingMethod, Collection<DeliveryStatus> statuses);

  /** 추적 중(SHIPPING·DELIVERED) 운송장을 배송방식·번호 단위로 중복 제거해 최대 {@code limit} 개 조회한다. */
  List<TrackedParcel> findTrackedParcels(int limit);

  /** 지점 도착(DELIVERED)이 {@code threshold} 이전이고 아직 독촉하지 않은 배송을 도착 오래된 순으로 조회한다. */
  List<Delivery> findPickupReminderTargets(Instant threshold, int limit);

  /** 미수령 독촉 1회 발송 마킹 CAS (NULL 조건이 dedup 가드). 호출 측 {@code @Transactional} 필수. */
  boolean markPickupReminderSent(Long id, Instant now);

  Optional<Delivery> findById(Long id);

  Optional<Delivery> findByParticipationId(Long participationId);

  List<Delivery> findAllByParticipationIds(List<Long> participationIds);

  /** 취소된 참여들의 배송 스냅샷을 일괄 삭제한다 (분철 취소 cascade 시 고아 스냅샷 정리). 호출 측 {@code @Transactional} 필수. */
  void deleteByParticipationIds(List<Long> participationIds);
}
