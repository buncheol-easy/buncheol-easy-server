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

  /**
   * 추적 중(SHIPPING·DELIVERED) 운송장을 배송방식·번호 단위로 중복 제거해 최대 {@code limit} 개 조회한다. 등록이
   * {@code registeredAfter} 이전인 운송장은 추적 포기로 제외한다.
   */
  List<TrackedParcel> findTrackedParcels(Instant registeredAfter, int limit);

  /** 지점 도착(DELIVERED)이 {@code threshold} 이전이고 아직 독촉하지 않은 배송을 도착 오래된 순으로 조회한다. */
  List<Delivery> findPickupReminderTargets(Instant threshold, int limit);

  /** 미수령 독촉 1회 발송 마킹 CAS (NULL 조건이 dedup 가드). 호출 측 {@code @Transactional} 필수. */
  boolean markPickupReminderSent(Long id, Instant now);

  Optional<Delivery> findById(Long id);

  /**
   * 묶음의 배송 스냅샷. <b>택배 1개 = 묶음 1개</b>이므로 묶음 하나에 배송은 하나다 (docs/70 결정 4).
   *
   * <p>⚠️ <b>전환 이전 데이터는 아직 하나가 아니다.</b> 배송을 참여 단위로 만들던 시절의 다슬롯 묶음은 슬롯마다
   * 배송이 생겨 한 묶음에 여러 건이 남아 있다 (실측: prod 묶음 64 · staging 66·83·87). P4 의 {@code
   * uq_deliveries_bundle} 승격 전까지 이 상태가 유지되므로 <b>id 가 가장 작은 행</b>을 그 묶음의 배송으로
   * 삼는다 — 먼저 만들어진 쪽이 배송비를 진 슬롯(운반자)의 것이다. 판정을 여기 한 곳에 두어 호출부마다
   * 갈리지 않게 한다.
   */
  Optional<Delivery> findByBundleId(Long bundleId);

  /**
   * 묶음들의 배송 스냅샷을 한 번에 조회한다 (목록 N+1 방지). <b>묶음당 최대 1건</b>을 보장한다 — 중복 축약은
   * 어댑터가 끝내고 나오므로 호출부가 merge 함수를 갖출 필요가 없다. 규칙은 {@link #findByBundleId} 와 같다.
   */
  List<Delivery> findAllByBundleIds(List<Long> bundleIds);

  /** 취소된 참여들의 배송 스냅샷을 일괄 삭제한다 (분철 취소 cascade 시 고아 스냅샷 정리). 호출 측 {@code @Transactional} 필수. */
  void deleteByParticipationIds(List<Long> participationIds);
}
