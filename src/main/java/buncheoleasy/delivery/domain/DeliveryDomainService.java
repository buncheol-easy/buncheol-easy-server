package buncheoleasy.delivery.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryDomainService {

  private final DeliveryRepository deliveryRepository;

  public Delivery createDelivery(final Delivery delivery) {
    return deliveryRepository.save(delivery);
  }

  public Delivery getDelivery(final Long id) {
    return deliveryRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
  }

  public Delivery getDeliveryByParticipationId(final Long participationId) {
    return deliveryRepository
        .findByParticipationId(participationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
  }

  /** 취소된 참여들의 배송 스냅샷을 일괄 삭제한다 (분철 취소 cascade 시 고아 스냅샷 정리). 호출 측 {@code @Transactional} 필수. */
  public void deleteByParticipationIds(final List<Long> participationIds) {
    deliveryRepository.deleteByParticipationIds(participationIds);
  }

  /**
   * 운송장 등록 (SNAPSHOTTED → SHIPPING CAS, SHIPPING 재등록은 번호 last-write-wins). 웹훅 자동 전이가 배송을 이미
   * DELIVERED/RECEIVED 로 진행시켰으면 CAS 가 실패하고 상태 위반 예외를 던진다. 호출 측 {@code @Transactional} 필수.
   */
  public void registerTracking(final Long deliveryId, final String trackingNumber, final Instant now) {
    if (trackingNumber == null || trackingNumber.isBlank()) {
      throw new BusinessException(ErrorCode.DELIVERY_TRACKING_NUMBER_REQUIRED);
    }
    if (!deliveryRepository.registerTrackingIfRegistrable(deliveryId, trackingNumber, now)) {
      throw new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }
  }

  /**
   * 수령 확인 (SHIPPING·DELIVERED → RECEIVED CAS). 운송장 등록 전이거나 이미 수령완료면 상태 위반 예외를 던진다. 호출 측
   * {@code @Transactional} 필수.
   */
  public void confirmReceipt(final Long deliveryId, final Instant now) {
    if (!deliveryRepository.confirmReceiptIfActive(deliveryId, now)) {
      throw new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }
  }

  /**
   * 운송사 추적의 지점 도착 감지 (SHIPPING → DELIVERED CAS). 멱등하며 실패 시(이미 도착/수령) 예외 없이 false 를 돌려준다 — 추적 콜백은
   * 중복 수신될 수 있어 이미 전이된 건을 오류로 다루지 않는다. 호출 측 {@code @Transactional} 필수.
   */
  public boolean markDeliveredIfShipping(
      final Long deliveryId, final Instant eventTime, final Instant now) {
    return deliveryRepository.markDeliveredIfShipping(deliveryId, eventTime, now);
  }

  /** 운송사 추적의 고객 수령 감지 (DELIVERED → RECEIVED CAS). 멱등, 실패 시 false. 호출 측 {@code @Transactional} 필수. */
  public boolean markReceivedIfDelivered(
      final Long deliveryId, final Instant eventTime, final Instant now) {
    return deliveryRepository.markReceivedIfDelivered(deliveryId, eventTime, now);
  }

  /**
   * 도착 콜백을 놓친 직행 전이 (SHIPPING → RECEIVED CAS, deliveredAt 도 함께 채움). 멱등, 실패 시 false. 호출 측
   * {@code @Transactional} 필수.
   */
  public boolean markReceivedIfShipping(
      final Long deliveryId, final Instant eventTime, final Instant now) {
    return deliveryRepository.markReceivedIfShipping(deliveryId, eventTime, now);
  }

  /** 운송장·배송방식이 같은 지정 상태의 배송 전부 조회 — 관리자 벌크 등록으로 한 운송장에 여러 배송이 매핑될 수 있다. */
  public List<Delivery> findAllByTrackingNumber(
      final String trackingNumber,
      final ShippingMethod shippingMethod,
      final Collection<DeliveryStatus> statuses) {
    return deliveryRepository.findAllByTrackingNumber(trackingNumber, shippingMethod, statuses);
  }

  /** 추적 중(SHIPPING·DELIVERED) 운송장을 배송방식·번호 단위로 중복 제거해 조회한다 (웹훅 갱신 스케줄러용). */
  public List<TrackedParcel> findTrackedParcels(final int limit) {
    return deliveryRepository.findTrackedParcels(limit);
  }

  /** 지점 도착이 {@code threshold} 이전이고 아직 독촉하지 않은 배송 조회 (미수령 독촉 스케줄러용). */
  public List<Delivery> findPickupReminderTargets(final Instant threshold, final int limit) {
    return deliveryRepository.findPickupReminderTargets(threshold, limit);
  }

  /** 미수령 독촉 1회 발송 마킹. 멱등하며 실패 시(이미 발송/수령) 예외 없이 false 를 돌려준다. 호출 측 {@code @Transactional} 필수. */
  public boolean markPickupReminderSent(final Long deliveryId, final Instant now) {
    return deliveryRepository.markPickupReminderSent(deliveryId, now);
  }
}
