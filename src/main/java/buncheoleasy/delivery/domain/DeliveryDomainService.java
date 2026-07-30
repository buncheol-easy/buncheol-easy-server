package buncheoleasy.delivery.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
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
}
