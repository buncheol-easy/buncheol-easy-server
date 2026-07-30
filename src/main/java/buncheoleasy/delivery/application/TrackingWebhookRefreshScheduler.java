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
 * 추적 중(SHIPPING·DELIVERED) 운송장의 웹훅을 주기적으로 재등록(TTL 연장)하고 최신 상태를 폴링한다. 웹훅은 만료시각(기본 48h)이 지나면 소멸하므로
 * 주기(기본 12h)가 TTL 의 절반 이하여야 만료 전 갱신 기회가 2회 이상 보장된다. 폴링은 콜백 유실·비동기 처리 실패의 안전망으로, 상태 정체 상한이 이 주기가
 * 된다.
 *
 * <p>운송장별 독립 처리({@link TrackingWebhookRefreshService#refresh})라 한 건 실패가 나머지를 막지 않고, 전이는 CAS 라 콜백과
 * 겹쳐도 안전하다. {@code app.delivery.tracking-refresh.enabled=false} 로 끌 수 있다 (테스트 환경 기본 비활성).
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
          // 웹훅 연장 실패(폴링은 성공) — TTL 만료는 자동 추적을 조용히 죽이므로 요약에 따로 집계한다.
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
