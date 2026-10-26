package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerCarriers;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.TrackingExecutorConfig;
import buncheoleasy.delivery.infrastructure.TrackEvent;
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
 * 운송장의 최근 추적 기록을 조회해 배송 상태에 반영한다 — 콜백(상태 미포함이라 Track API 재조회)과 갱신 스케줄러 폴링이 같은 경로를 쓴다. 기록에서
 * 도달한 가장 먼 단계로 판정하므로 중간 콜백을 놓쳐도 따라잡히고, 전이는 전부 CAS 라 중복·경합에도 수렴한다.
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

  /** cupost 원문 문구 — 두 단계 모두 UNKNOWN 코드로 온다 (문서에 없는 캐리어 동작, 2026-10 실측). */
  private static final String CU_ARRIVED_NAME = "점포도착";

  private static final String CU_PICKED_UP_NAME = "수령완료";

  /** 캐리어 이벤트를 우리 전이로 해석한 결과. */
  private enum CarrierEvent {
    ARRIVED, // 지점 도착 → 우리 DELIVERED
    PICKED_UP, // 고객 수령 → 우리 RECEIVED
    OTHER // 이동중 등 — 전이 없음
  }

  /** 기록에서 도달한 가장 먼 단계와, 그 단계에 처음 도달한 이벤트. */
  private record Progress(CarrierEvent stage, TrackEvent firstEvent) {}

  private final DeliveryTrackerClient deliveryTrackerClient;
  private final DeliveryDomainService deliveryDomainService;
  private final TrackingTransitionService trackingTransitionService;
  private final Clock clock;

  /** 콜백 수신 경로 — 비동기 처리, 실패는 로깅만 (갱신 스케줄러 폴링이 안전망). */
  @Async(TrackingExecutorConfig.TRACKING_EXECUTOR)
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
      log.debug("배송 추적 동기화 - 추적 중 배송 없음 - trackingNumber={}", trackingNumber);
      return;
    }

    List<TrackEvent> events = deliveryTrackerClient.findRecentEvents(carrierId, trackingNumber);
    if (events.isEmpty()) {
      log.info("배송 추적 동기화 - 추적 정보 없음 - trackingNumber={}", trackingNumber);
      return;
    }
    Optional<Progress> progress = findProgress(shippingMethod.get(), events);
    if (progress.isEmpty()) {
      TrackEvent latest = events.getLast();
      // 정규화 실패(UNKNOWN)는 매핑 추가가 필요한 신호라 info, 정상 코드 스킵은 평시 소음이라 debug.
      if (CARRIER_UNKNOWN.equals(latest.statusCode())) {
        log.info(
            "배송 추적 동기화 - 정규화 안 된 상태라 스킵 - trackingNumber={} statusName={}",
            trackingNumber,
            latest.statusName());
      } else {
        log.debug(
            "배송 추적 동기화 - 전이 대상 아닌 상태 - trackingNumber={} statusCode={}",
            trackingNumber,
            latest.statusCode());
      }
      return;
    }

    CarrierEvent event = progress.get().stage();
    TrackEvent firstEvent = progress.get().firstEvent();
    Instant now = Instant.now(clock);
    Instant eventTime = firstEvent.time() != null ? firstEvent.time() : now;
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
    // 최신 이벤트도 남긴다 — cupost 수령 원문이 바뀌면 도착에 머물러 이 로그의 latest 가 유일한 신호다.
    TrackEvent latest = events.getLast();
    log.info(
        "배송 추적 동기화 완료 - trackingNumber={} event={} first={}/{} latest={}/{} 대상: {}, 전이: {}, 실패: {}",
        trackingNumber,
        event,
        firstEvent.statusCode(),
        firstEvent.statusName(),
        latest.statusCode(),
        latest.statusName(),
        targets.size(),
        transitioned,
        failed);
  }

  /** 기록은 오래된 순이라, 같은 단계 이벤트가 여럿이면(cupost 의 점포도착·배달완료) 처음 도달한 시각을 쓴다. */
  private Optional<Progress> findProgress(
      final ShippingMethod shippingMethod, final List<TrackEvent> events) {
    TrackEvent arrived = null;
    for (TrackEvent trackEvent : events) {
      CarrierEvent event = resolveEvent(shippingMethod, trackEvent);
      if (event == CarrierEvent.PICKED_UP) {
        return Optional.of(new Progress(CarrierEvent.PICKED_UP, trackEvent));
      }
      if (event == CarrierEvent.ARRIVED && arrived == null) {
        arrived = trackEvent;
      }
    }
    return Optional.ofNullable(arrived).map(found -> new Progress(CarrierEvent.ARRIVED, found));
  }

  private CarrierEvent resolveEvent(final ShippingMethod shippingMethod, final TrackEvent event) {
    return switch (shippingMethod) {
      case CU_HALF -> resolveCuEvent(event);
      case GS25_HALF -> resolveGs25Event(event);
    };
  }

  /**
   * cupost 는 DELIVERED('배달완료')가 기사가 점포에 넣은 시점이고 고객 수령은 원문 '수령완료'로만 온다. 원문이 바뀌면 수령으로 오판하지 않고
   * 도착에 머문다 — 참여자 수령 확인·미수령 독촉으로 수습되는 쪽이다.
   */
  private CarrierEvent resolveCuEvent(final TrackEvent event) {
    if (CU_PICKED_UP_NAME.equals(event.statusName())) {
      return CarrierEvent.PICKED_UP;
    }
    if (CU_ARRIVED_NAME.equals(event.statusName())
        || CARRIER_AVAILABLE_FOR_PICKUP.equals(event.statusCode())
        || CARRIER_DELIVERED.equals(event.statusCode())) {
      return CarrierEvent.ARRIVED;
    }
    return CarrierEvent.OTHER;
  }

  /** 이름이 어긋난다: 캐리어 AVAILABLE_FOR_PICKUP(지점 도착) = 우리 DELIVERED, 캐리어 DELIVERED(고객 수령) = 우리 RECEIVED. */
  private CarrierEvent resolveGs25Event(final TrackEvent event) {
    if (CARRIER_AVAILABLE_FOR_PICKUP.equals(event.statusCode())) {
      return CarrierEvent.ARRIVED;
    }
    if (CARRIER_DELIVERED.equals(event.statusCode())) {
      return CarrierEvent.PICKED_UP;
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
