package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.participation.Participation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 입금 기한(dueAt)이 지난 입금확인중 참여를 자동 취소한다. 30분(또는 deadline) 단위라 분 단위 폴링이면 충분하다. 참여별 독립 트랜잭션({@link
 * ParticipationPaymentExpiryService#expire})이라 한 건 실패가 나머지를 막지 않고, CAS 라 다중 인스턴스가 같은 건을 중복 처리해도
 * 안전하다.
 *
 * <p>{@code app.participation.payment-expiry.enabled=false} 로 끌 수 있다 (테스트 환경 기본 비활성).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.participation.payment-expiry",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ParticipationPaymentExpiryScheduler {

  private final ParticipationPaymentExpiryService participationPaymentExpiryService;
  private final Clock clock;

  @Scheduled(
      fixedDelayString = "${app.participation.payment-expiry.interval-ms}",
      initialDelayString = "${app.participation.payment-expiry.initial-delay-ms}")
  public void expireOverduePayments() {
    Instant now = Instant.now(clock);
    List<Participation> targets = participationPaymentExpiryService.findOverdueTargets(now);
    if (targets.isEmpty()) {
      return;
    }

    int expiredCount = 0;
    int failedCount = 0;
    for (Participation target : targets) {
      try {
        if (participationPaymentExpiryService.expire(target.getId(), now)) {
          expiredCount++;
        }
      } catch (Exception e) {
        // 한 건 실패가 배치 전체를 중단시키지 않도록 격리하고 다음 참여로 진행한다.
        failedCount++;
        log.error("입금 만료 처리 실패 - participationId: {}", target.getId(), e);
      }
    }
    log.info("입금 만료 처리 완료 - 대상: {}, 만료: {}, 실패: {}", targets.size(), expiredCount, failedCount);
  }
}
