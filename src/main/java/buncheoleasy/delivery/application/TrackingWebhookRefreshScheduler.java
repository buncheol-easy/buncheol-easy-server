package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 추적 중(SHIPPING·DELIVERED) 운송장의 웹훅을 주기 재등록(TTL 연장)하고 최신 상태를 폴링한다. 주기(기본 12h)는 웹훅 TTL(48h)의 절반
 * 이하여야 만료 전 갱신 기회가 2회 이상 보장되고, 폴링은 콜백 유실의 안전망이라 상태 정체 상한이 이 주기다. 운송장별 격리 처리라 한 건 실패가 나머지를 막지
 * 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.delivery.tracking-refresh",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TrackingWebhookRefreshScheduler {

  private final TrackingWebhookRefreshService trackingWebhookRefreshService;
  private final DeliveryTrackerClient deliveryTrackerClient;
  private final DeliveryTrackerProperties deliveryTrackerProperties;
  private final Clock clock;

  @Scheduled(
      fixedDelayString = "${app.delivery.tracking-refresh.interval-ms}",
      initialDelayString = "${app.delivery.tracking-refresh.initial-delay-ms}")
  public void refreshTrackedParcels() {
    if (!deliveryTrackerClient.isEnabled()) {
      log.debug("Delivery Tracker 미설정 - 추적 웹훅 갱신 건너뜀");
      return;
    }
    List<TrackedParcel> targets = trackingWebhookRefreshService.findRefreshTargets();
    if (targets.isEmpty()) {
      return;
    }

    Instant expirationTime = Instant.now(clock).plus(deliveryTrackerProperties.webhookTtl());
    int renewedCount = 0;
    int renewFailedCount = 0;
    int pollFailedCount = 0;
    for (TrackedParcel target : targets) {
      try {
        if (trackingWebhookRefreshService.refresh(target, expirationTime)) {
          renewedCount++;
        } else {
          // 연장 실패는 자동 추적을 조용히 죽이므로 요약에 따로 집계한다.
          renewFailedCount++;
        }
      } catch (Exception e) {
        // 한 건 실패가 배치 전체를 중단시키지 않도록 격리하고 다음 운송장으로 진행한다.
        pollFailedCount++;
        log.error("추적 웹훅 갱신 실패 - trackingNumber: {}", target.trackingNumber(), e);
      }
    }
    log.info(
        "추적 웹훅 갱신 완료 - 대상: {}, 연장: {}, 연장실패: {}, 폴링실패: {}",
        targets.size(),
        renewedCount,
        renewFailedCount,
        pollFailedCount);
  }
}
