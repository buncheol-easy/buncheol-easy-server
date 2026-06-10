package buncheoleasy.notification.application;

import buncheoleasy.buncheol.application.PaymentDueImminentEvent;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 입금 기한이 마감 3시간 이내로 임박한 AWAITING_PAYMENT 참여에 입금 독려 알림을 발행한다. 중복 발송 방지는 발송 직전 {@code DueReminderGuard}
 * (Redis SETNX)가 담당하므로 폴링이 같은 대상을 반복 집어와도 안전하다. 한 건 발행 실패가 배치 전체를 막지 않는다.
 *
 * <p>{@code app.notification.payment-due-reminder.enabled=false} 로 끌 수 있다(테스트 환경 기본 비활성).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.notification.payment-due-reminder",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PaymentDueReminderScheduler {

  // 입금 기한 마감 몇 시간 전부터 임박 알림을 보낼지.
  private static final Duration REMINDER_WINDOW = Duration.ofHours(3);

  private final ParticipationDomainService participationDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Scheduled(
      fixedDelayString = "${app.notification.payment-due-reminder.interval-ms:600000}",
      initialDelayString = "${app.notification.payment-due-reminder.initial-delay-ms:30000}")
  public void remindImminentPaymentDue() {
    Instant now = Instant.now(clock);
    Instant dueBefore = now.plus(REMINDER_WINDOW);
    List<Participation> targets =
        participationDomainService.findAwaitingPaymentReminderTargets(now, dueBefore);
    if (targets.isEmpty()) {
      return;
    }

    int published = 0;
    for (Participation target : targets) {
      try {
        eventPublisher.publishEvent(new PaymentDueImminentEvent(target.getId()));
        published++;
      } catch (Exception e) {
        // 한 건 실패가 배치 전체를 중단시키지 않도록 격리한다.
        log.error("입금 기한 임박 알림 발행 실패 - participationId: {}", target.getId(), e);
      }
    }
    log.info("입금 기한 임박 알림 발행 - 대상: {}, 발행: {}", targets.size(), published);
  }
}
