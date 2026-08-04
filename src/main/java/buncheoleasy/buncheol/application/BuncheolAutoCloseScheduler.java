package buncheoleasy.buncheol.application;

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
 * deadline 이 지난 RECRUITING 분철을 마감 판정(입금확인 인원 ≥ 최소 인원이면 진행확정, 미만이면 취소)한다. 마감시간은 도메인에서 정각으로 강제되므로 매시
 * 정각 cron 으로 정밀 마감(통지 지연 최소화)하고, fallback 폴링을 다운타임으로 cron 을 놓친 경우의 안전망으로 둔다(재기동 시 initial-delay 직후
 * 1회로 빠르게 복구). 두 트리거 모두 멱등 + CAS 라 같은 분철을 중복 처리해도 안전하다. 분철별 독립 트랜잭션({@link
 * BuncheolAutoCloseService#finalizeExpired}) 이라 한 건 실패가 나머지를 막지 않는다.
 *
 * <p>{@code app.buncheol.auto-close.enabled=false} 로 끌 수 있다 (테스트 환경 기본 비활성).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.buncheol.auto-close",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BuncheolAutoCloseScheduler {

  private final BuncheolAutoCloseService buncheolAutoCloseService;
  private final SchedulerActivationGate schedulerActivationGate;
  private final Clock clock;

  // 정시 정밀 마감: 마감시간이 도메인에서 정각으로 강제돼 매시 정각(KST)에 즉시 마감해 통지 지연을 최소화. 식은 app.buncheol.auto-close.cron
  // 으로 조정.
  @Scheduled(cron = "${app.buncheol.auto-close.cron}", zone = "Asia/Seoul")
  public void closeAtHour() {
    runAutoClose();
  }

  // 안전망 폴링: 정각 cron 누락(다운타임)·동시각 대량 마감(배치 초과)을 저빈도로 복구. 멱등 + CAS 라 cron 과 겹쳐도 안전.
  @Scheduled(
      fixedDelayString = "${app.buncheol.auto-close.fallback-interval-ms}",
      initialDelayString = "${app.buncheol.auto-close.initial-delay-ms}")
  public void closeByFallbackPolling() {
    runAutoClose();
  }

  private void runAutoClose() {
    // 두 트리거(정각 cron·fallback 폴링)의 공통 진입부에서 게이트 — cron 은 initial-delay 의
    // 보호를 받지 않으므로 이 게이트가 "전환 전 인스턴스의 부작용 차단"의 유일한 방어다 (docs/39).
    if (!schedulerActivationGate.isActive()) {
      log.info("기동 유예 중 — 분철 자동 마감 건너뜀 (블루-그린 전환 전 부작용 차단)");
      return;
    }
    Instant now = Instant.now(clock);
    List<Long> expiredIds = buncheolAutoCloseService.findExpiredBuncheolIds(now);
    if (expiredIds.isEmpty()) {
      return;
    }

    int closedCount = 0;
    int failedCount = 0;
    for (Long buncheolId : expiredIds) {
      try {
        if (buncheolAutoCloseService.finalizeExpired(buncheolId, now)) {
          closedCount++;
        }
      } catch (Exception e) {
        // 한 건 실패가 배치 전체를 중단시키지 않도록 격리하고 다음 분철로 진행한다.
        failedCount++;
        log.error("분철 자동 마감 실패 - buncheolId: {}", buncheolId, e);
      }
    }
    log.info("분철 자동 마감 완료 - 대상: {}, 마감: {}, 실패: {}", expiredIds.size(), closedCount, failedCount);
  }
}
