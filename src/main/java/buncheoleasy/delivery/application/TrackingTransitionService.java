package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.DeliveryDomainService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운송사 추적으로 감지한 상태를 배송 1건에 반영한다. {@link TrackingSyncService} 에서 분리한 이유는 건별 독립 트랜잭션 경계 확보 —
 * 같은 클래스 내부 호출(self-invocation)은 {@code @Transactional} 프록시를 타지 않는다 (입금 만료 스케줄러 → 서비스 분리와 같은 구조).
 *
 * <p>모든 전이가 CAS 라 멱등하다. false 는 이미 전이됐거나(콜백 중복·수동 수령확인 선행) 대상 상태가 아니라는 뜻이라 오류로 다루지 않는다.
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

  /**
   * 고객 수령 감지: DELIVERED → RECEIVED. 도착 콜백을 놓쳐 아직 SHIPPING 이면 직행 전이(deliveredAt 도 수령 시각으로 근사)로
   * 폴백한다.
   */
  @Transactional
  public boolean markReceived(final Long deliveryId, final Instant eventTime, final Instant now) {
    if (deliveryDomainService.markReceivedIfDelivered(deliveryId, eventTime, now)) {
      return true;
    }
    return deliveryDomainService.markReceivedIfShipping(deliveryId, eventTime, now);
  }
}
