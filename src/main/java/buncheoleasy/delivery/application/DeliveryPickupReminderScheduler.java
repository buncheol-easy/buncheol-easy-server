package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.Delivery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지점 도착 후 미수령 배송의 독촉 알림을 구동한다. 반값택배는 보관기간(통상 3일)이 지나면 반송되므로, 도착 후 기준 시간(기본 24h)이 지나도록 수령이 감지되지
 * 않으면 참여자에게 찾아가라는 알림톡을 1회 보낸다.
 *
 * <p>기본 꺼짐({@code app.delivery.pickup-reminder.enabled=false}) — 발송 마킹(1회 dedup)이 알림톡 발송 스킵과
 * 무관하게 소진되므로, 알리고 PICKUP_REMINDER_* 템플릿 승인·코드 설정 후에 켜야 한다.
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
  private final Clock clock;

  @Scheduled(
      fixedDelayString = "${app.delivery.pickup-reminder.interval-ms}",
      initialDelayString = "${app.delivery.pickup-reminder.initial-delay-ms}")
  public void remindUnclaimedDeliveries() {
    Instant now = Instant.now(clock);
    List<Delivery> targets = deliveryPickupReminderService.findReminderTargets(now);
    if (targets.isEmpty()) {
      return;
    }

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
}
