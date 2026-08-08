package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerProperties;
import buncheoleasy.global.scheduler.SchedulerActivationGate;
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
  private final SchedulerActivationGate schedulerActivationGate;
  private final Clock clock;

  @Scheduled(
      fixedDelayString = "${app.delivery.tracking-refresh.interval-ms}",
      initialDelayString = "${app.delivery.tracking-refresh.initial-delay-ms}")
  public void refreshTrackedParcels() {
    // 전환 전 인스턴스의 외부 API 호출(쿼터 이중 소모)·상태 전이를 게이트로 차단 (docs/39).
    // 12h 주기라 유예만큼의 재개 지연은 무해하다.
    if (!schedulerActivationGate.isActive()) {
      log.info("기동 유예 중 — 추적 웹훅 갱신 건너뜀 (블루-그린 전환 전 부작용 차단)");
      return;
    }
    if (!deliveryTrackerClient.isEnabled()) {
      log.debug("Delivery Tracker 미설정 - 추적 웹훅 갱신 건너뜀");
      return;
    }
    List<TrackedParcel> targets =
        trackingWebhookRefreshService.findRefreshTargets(Instant.now(clock));
    if (targets.isEmpty()) {
      return;
    }

    int renewedCount = 0;
    int renewFailedCount = 0;
    int pollFailedCount = 0;
    int processedCount = 0;
    for (TrackedParcel target : targets) {
      // 종료 시 스케줄링 풀은 shutdownNow 로 인터럽트된다(await-termination 미설정). 플래그가 서면 이후의
      // 스로틀 대기가 전부 즉시 실패해 남은 건 전체가 오탐 ERROR 로 쏟아지므로, 여기서 끊고 다음 주기에 맡긴다.
      if (Thread.currentThread().isInterrupted()) {
        log.warn("추적 웹훅 갱신 중단(인터럽트) - 처리 {}/{}", processedCount, targets.size());
        return;
      }
      try {
        // 만료시각은 건별로 계산한다 — 배치가 길어져도 뒤쪽 운송장의 실효 TTL 이 깎이지 않게.
        Instant expirationTime = Instant.now(clock).plus(deliveryTrackerProperties.webhookTtl());
        TrackingWebhookRefreshService.RefreshOutcome outcome =
            trackingWebhookRefreshService.refresh(target, expirationTime);
        if (outcome.renewed()) {
          renewedCount++;
        } else {
          // 연장 실패는 자동 추적을 조용히 죽이므로 요약에 따로 집계한다.
          renewFailedCount++;
        }
        if (!outcome.polled()) {
          pollFailedCount++;
        }
      } catch (Exception e) {
        // 예상 밖 실패 백스톱 — 한 건이 배치 전체를 중단시키지 않게 격리한다. 연장·폴링 모두 보장 못 한 것으로 집계.
        renewFailedCount++;
        pollFailedCount++;
        log.error("추적 웹훅 갱신 실패 - trackingNumber: {}", target.trackingNumber(), e);
      }
      processedCount++;
    }
    log.info(
        "추적 웹훅 갱신 완료 - 대상: {}, 연장: {}, 연장실패: {}, 폴링실패: {}",
        targets.size(),
        renewedCount,
        renewFailedCount,
        pollFailedCount);
  }
}
