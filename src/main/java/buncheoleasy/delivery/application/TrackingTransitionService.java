package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.DeliveryDomainService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운송사 추적으로 감지한 상태를 배송 1건에 반영한다. {@link TrackingSyncService} 와 분리한 이유는 건별 {@code @Transactional} 경계
 * 확보(self-invocation 은 프록시를 안 탄다). 전이는 전부 CAS 라 멱등 — false 는 이미 전이됐다는 뜻이라 오류가 아니다.
 */
@Service
@RequiredArgsConstructor
public class TrackingTransitionService {

  private final DeliveryDomainService deliveryDomainService;

  /** 지점 도착 감지: SHIPPING → DELIVERED. */
  @Transactional
  public boolean markDelivered(final Long deliveryId, final Instant eventTime, final Instant now) {
    return deliveryDomainService.markDeliveredIfShipping(deliveryId, eventTime, now);
  }

  /** 고객 수령 감지: DELIVERED → RECEIVED. 도착 감지를 놓친 SHIPPING 은 직행 전이로 폴백한다. */
  @Transactional
  public boolean markReceived(final Long deliveryId, final Instant eventTime, final Instant now) {
    if (deliveryDomainService.markReceivedIfDelivered(deliveryId, eventTime, now)) {
      return true;
    }
    return deliveryDomainService.markReceivedIfShipping(deliveryId, eventTime, now);
  }
}
