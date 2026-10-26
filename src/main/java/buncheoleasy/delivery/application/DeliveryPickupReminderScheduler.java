package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerCarriers;
import buncheoleasy.global.scheduler.SchedulerActivationGate;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 도착 후 기준 시간(기본 12h)이 지나도록 미수령인 배송에 독촉 알림을 보낸다 — 반값택배는 보관기간(통상 3일) 후 반송되기 때문. 매일 정오(KST) 일괄
 * 실행이라 발송 시각이 예측 가능하고, 기준 12h 덕에 낮에 도착한 택배는 반드시 "도착 다음 날 정오"에 걸린다.
 *
 * <p>⚠️ 기본 꺼짐 — 발송 마킹(1회 dedup)이 알림톡 발송 스킵과 무관하게 소진되므로, 알리고 PICKUP_REMINDER_* 템플릿 승인·코드 설정 후에 켠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.delivery.pickup-reminder",
    name = "enabled",
    havingValue = "true")
public class DeliveryPickupReminderScheduler {

  private final DeliveryPickupReminderService deliveryPickupReminderService;
  private final TrackingSyncService trackingSyncService;
  private final SchedulerActivationGate schedulerActivationGate;
  private final Clock clock;

  @Scheduled(cron = "${app.delivery.pickup-reminder.cron}", zone = "Asia/Seoul")
  public void remindUnclaimedDeliveries() {
    // cron 트리거는 initial-delay 보호가 없다 — 정오 직전 기동한 전환 전 인스턴스가 독촉
    // 마킹·발송을 저지르는 것을 게이트로 차단 (docs/39). 하루 1회라 skip 시 다음 날 처리.
    if (!schedulerActivationGate.isActive()) {
      log.info("기동 유예 중 — 미수령 독촉 건너뜀 (블루-그린 전환 전 부작용 차단)");
      return;
    }
    Instant now = Instant.now(clock);
    List<Delivery> targets = deliveryPickupReminderService.findReminderTargets(now);
    if (targets.isEmpty()) {
      return;
    }
    syncBeforeRemind(targets);

    int remindedCount = 0;
    int failedCount = 0;
    for (Delivery target : targets) {
      try {
        if (deliveryPickupReminderService.remind(target.getId(), now)) {
          remindedCount++;
        }
      } catch (Exception e) {
        // 한 건 실패가 배치 전체를 중단시키지 않도록 격리하고 다음 배송으로 진행한다.
        failedCount++;
        log.error("미수령 독촉 처리 실패 - deliveryId: {}", target.getId(), e);
      }
    }
    log.info("미수령 독촉 처리 완료 - 대상: {}, 발송: {}, 실패: {}", targets.size(), remindedCount, failedCount);
  }

  /**
   * 수령 콜백이 늦거나 오지 않아도 이미 찾아간 사람을 독촉하지 않도록, 독촉 직전에 운송장을 한 번 동기화한다 — 수령으로 바뀐 배송은 독촉 마킹
   * CAS(status=DELIVERED)가 걸러낸다. 동기화 실패는 독촉을 막지 않는다.
   */
  private void syncBeforeRemind(final List<Delivery> targets) {
    Set<TrackedParcel> parcels = new LinkedHashSet<>();
    for (Delivery target : targets) {
      if (target.getTrackingNumber() != null) {
        parcels.add(new TrackedParcel(target.getShippingMethod(), target.getTrackingNumber()));
      }
    }
    for (TrackedParcel parcel : parcels) {
      try {
        trackingSyncService.sync(
            DeliveryTrackerCarriers.carrierId(parcel.shippingMethod()), parcel.trackingNumber());
      } catch (RuntimeException e) {
        log.warn("미수령 독촉 전 추적 동기화 실패 - 독촉은 진행 - trackingNumber: {}", parcel.trackingNumber(), e);
      }
    }
  }
}
