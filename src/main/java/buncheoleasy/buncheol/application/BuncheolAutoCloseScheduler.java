package buncheoleasy.buncheol.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * deadline 이 지난 RECRUITING 분철을 주기적으로 폴링해 마감하고 낙찰자를 선정한다. 분철별로 독립 트랜잭션({@link
 * BuncheolAutoCloseService#closeExpired})에서 처리하므로 한 건이 실패해도 나머지는 계속 진행된다.
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
  private final Clock clock;

  // 분철 마감시간이 '시' 단위라 분 단위 폴링이면 충분. 주기는 app.buncheol.auto-close.interval-ms 로 조정.
  @Scheduled(
      fixedDelayString = "${app.buncheol.auto-close.interval-ms:60000}",
      initialDelayString = "${app.buncheol.auto-close.initial-delay-ms:10000}")
  public void closeExpiredBuncheols() {
    Instant now = Instant.now(clock);
    List<Long> expiredIds = buncheolAutoCloseService.findExpiredBuncheolIds(now);
    if (expiredIds.isEmpty()) {
      return;
    }

    int closedCount = 0;
    for (Long buncheolId : expiredIds) {
      try {
        if (buncheolAutoCloseService.closeExpired(buncheolId, now)) {
          closedCount++;
        }
      } catch (Exception e) {
        // 한 건 실패가 폴링 배치 전체를 중단시키지 않도록 격리하고 다음 분철로 진행한다.
        log.error("분철 자동 마감 실패 - buncheolId: {}", buncheolId, e);
      }
    }
    log.info("분철 자동 마감 폴링 완료 - 대상: {}, 마감: {}", expiredIds.size(), closedCount);
  }
}
