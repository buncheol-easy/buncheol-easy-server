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

  private static final String CARRIER_UNKNOWN = "UNKNOWN";

  /** 캐리어의 최신 이벤트를 우리 전이로 해석한 결과. */
  private enum CarrierEvent {
    ARRIVED, // 지점 도착 → 우리 DELIVERED
    PICKED_UP, // 고객 수령 → 우리 RECEIVED
    OTHER // 이동중 등 — 전이 없음
  }

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
    CarrierEvent event = resolveEvent(lastEvent);
    if (event == CarrierEvent.OTHER) {
      // 정규화 실패(UNKNOWN + 미지 문구)는 매핑 추가가 필요한 신호라 info 로 노출하고,
      // 정상 코드(이동중 등)의 스킵은 평시 소음이라 debug 로 낮춘다.
      if (CARRIER_UNKNOWN.equals(lastEvent.statusCode())) {
        log.info(
            "배송 추적 동기화 - 정규화 안 된 상태라 스킵 - trackingNumber={} statusName={}",
            trackingNumber,
            lastEvent.statusName());
      } else {
        log.debug(
            "배송 추적 동기화 - 전이 대상 아닌 상태 - trackingNumber={} statusCode={}",
            trackingNumber,
            lastEvent.statusCode());
      }
      return;
    }

    Instant now = Instant.now(clock);
    Instant eventTime = lastEvent.time() != null ? lastEvent.time() : now;
    int transitioned = 0;
    int failed = 0;
    for (Delivery target : targets) {
      try {
        if (applyTransition(target.getId(), event, eventTime, now)) {
          transitioned++;
        }
      } catch (Exception e) {
        // 한 건 실패가 같은 운송장의 나머지 배송 전이를 막지 않도록 격리한다.
        failed++;
        log.error("배송 추적 전이 실패 - deliveryId={} event={}", target.getId(), event, e);
      }
    }
    log.info(
        "배송 추적 동기화 완료 - trackingNumber={} statusCode={} event={} 대상: {}, 전이: {}, 실패: {}",
        trackingNumber,
        lastEvent.statusCode(),
        event,
        targets.size(),
        transitioned,
        failed);
  }

  /**
   * 캐리어 이벤트 해석. 캐리어와 우리 상태의 이름이 어긋난다 — 캐리어 AVAILABLE_FOR_PICKUP(지점 도착) = 우리 DELIVERED, 캐리어
   * DELIVERED(고객이 찾아감) = 우리 RECEIVED.
   *
   * <p>cupost 는 Delivery Tracker 의 코드 정규화가 안 돼 전 이벤트가 UNKNOWN + 원문 한글 문구로 온다(2026-07 실측). 알려진 문구만
   * 정확 일치로 폴백 매핑하고, 그 외 문구는 지금처럼 스킵된다(fail-safe) — 스킵 로그의 statusName 으로 새 문구를 발견하면 여기에 추가한다.
   */
  private CarrierEvent resolveEvent(final TrackLastEvent lastEvent) {
    if (CARRIER_AVAILABLE_FOR_PICKUP.equals(lastEvent.statusCode())) {
      return CarrierEvent.ARRIVED;
    }
    if (CARRIER_DELIVERED.equals(lastEvent.statusCode())) {
      return CarrierEvent.PICKED_UP;
    }
    if (CARRIER_UNKNOWN.equals(lastEvent.statusCode())) {
      if ("점포도착".equals(lastEvent.statusName())) {
        return CarrierEvent.ARRIVED;
      }
      if ("수령완료".equals(lastEvent.statusName())) {
        return CarrierEvent.PICKED_UP;
      }
    }
    return CarrierEvent.OTHER;
  }

  private boolean applyTransition(
      final Long deliveryId, final CarrierEvent event, final Instant eventTime, final Instant now) {
    if (event == CarrierEvent.ARRIVED) {
      return trackingTransitionService.markDelivered(deliveryId, eventTime, now);
    }
    return trackingTransitionService.markReceived(deliveryId, eventTime, now);
  }
}
