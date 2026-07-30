package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerCarriers;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.TrackLastEvent;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 운송장의 최신 추적 상태를 조회해 배송 상태에 반영한다. 추적 콜백(웹훅)과 갱신 스케줄러의 폴링 안전망이 같은 경로를 쓴다.
 *
 * <p>콜백 본문에는 캐리어·운송장 번호만 오고 상태가 없으므로 Track API 를 재조회해 최신 이벤트 하나로 판정한다 — 중간 콜백을 놓쳐도 다음 조회가 최신 상태를
 * 반영하므로 따라잡힌다. 전이는 전부 CAS 라 콜백 중복·수동 수령확인과 겹쳐도 수렴한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingSyncService {

  /** 추적으로 상태가 더 진행될 수 있는 배송 상태 — 수령 완료(RECEIVED)는 종결이라 제외. */
  private static final Set<DeliveryStatus> TRACKED_STATUSES =
      Set.of(DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED);

  /** Delivery Tracker 추적 상태 코드 중 우리 전이에 매핑되는 값. 그 외 코드(이동중 등)는 전이 없이 로그만 남긴다. */
  private static final String CARRIER_AVAILABLE_FOR_PICKUP = "AVAILABLE_FOR_PICKUP";

  private static final String CARRIER_DELIVERED = "DELIVERED";

  private final DeliveryTrackerClient deliveryTrackerClient;
  private final DeliveryDomainService deliveryDomainService;
  private final TrackingTransitionService trackingTransitionService;
  private final Clock clock;

  /** 콜백 수신 경로 — 202 응답을 1초 내에 돌려줘야 하므로 비동기로 처리하고, 실패는 로깅만 한다(갱신 스케줄러 폴링이 안전망). */
  @Async
  public void syncAsync(final String carrierId, final String trackingNumber) {
    try {
      sync(carrierId, trackingNumber);
    } catch (RuntimeException e) {
      log.error(
          "배송 추적 동기화 실패 - carrierId={} trackingNumber={} (갱신 스케줄러가 재시도)",
          carrierId,
          trackingNumber,
          e);
    }
  }

  /** 갱신 스케줄러의 폴링 경로 — 실패를 호출자에게 전파해 건별 격리·집계를 맡긴다. */
  public void sync(final String carrierId, final String trackingNumber) {
    Optional<ShippingMethod> shippingMethod = DeliveryTrackerCarriers.shippingMethod(carrierId);
    if (shippingMethod.isEmpty()) {
      log.warn("배송 추적 동기화 - 미지원 캐리어라 무시 - carrierId={}", carrierId);
      return;
    }
    if (!deliveryTrackerClient.isEnabled()) {
      log.debug("Delivery Tracker 미설정 - 동기화 건너뜀 - trackingNumber={}", trackingNumber);
      return;
    }
    List<Delivery> targets =
        deliveryDomainService.findAllByTrackingNumber(
            trackingNumber, shippingMethod.get(), TRACKED_STATUSES);
    if (targets.isEmpty()) {
      // 전부 수령완료됐거나 우리가 모르는 운송장(취소 정리 등) — 조회 비용을 아끼고 끝낸다.
      log.debug("배송 추적 동기화 - 추적 중 배송 없음 - trackingNumber={}", trackingNumber);
      return;
    }

    Optional<TrackLastEvent> found = deliveryTrackerClient.findLastEvent(carrierId, trackingNumber);
    if (found.isEmpty()) {
      log.info("배송 추적 동기화 - 추적 정보 없음 - trackingNumber={}", trackingNumber);
      return;
    }
    TrackLastEvent lastEvent = found.get();
    String statusCode = lastEvent.statusCode();
    if (!CARRIER_AVAILABLE_FOR_PICKUP.equals(statusCode) && !CARRIER_DELIVERED.equals(statusCode)) {
      log.debug(
          "배송 추적 동기화 - 전이 대상 아닌 상태 - trackingNumber={} statusCode={}", trackingNumber, statusCode);
      return;
    }

    Instant now = Instant.now(clock);
    Instant eventTime = lastEvent.time() != null ? lastEvent.time() : now;
    int transitioned = 0;
    int failed = 0;
    for (Delivery target : targets) {
      try {
        if (applyTransition(target.getId(), statusCode, eventTime, now)) {
          transitioned++;
        }
      } catch (Exception e) {
        // 한 건 실패가 같은 운송장의 나머지 배송 전이를 막지 않도록 격리한다.
        failed++;
        log.error("배송 추적 전이 실패 - deliveryId={} statusCode={}", target.getId(), statusCode, e);
      }
    }
    log.info(
        "배송 추적 동기화 완료 - trackingNumber={} statusCode={} 대상: {}, 전이: {}, 실패: {}",
        trackingNumber,
        statusCode,
        targets.size(),
        transitioned,
        failed);
  }

  private boolean applyTransition(
      final Long deliveryId, final String statusCode, final Instant eventTime, final Instant now) {
    // 캐리어와 우리 상태의 이름이 어긋난다 — 캐리어 AVAILABLE_FOR_PICKUP(지점 도착) = 우리 DELIVERED,
    // 캐리어 DELIVERED(고객이 찾아감) = 우리 RECEIVED.
    if (CARRIER_AVAILABLE_FOR_PICKUP.equals(statusCode)) {
      return trackingTransitionService.markDelivered(deliveryId, eventTime, now);
    }
    return trackingTransitionService.markReceived(deliveryId, eventTime, now);
  }
}
