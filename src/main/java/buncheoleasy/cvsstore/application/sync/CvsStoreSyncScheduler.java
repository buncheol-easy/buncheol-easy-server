package buncheoleasy.cvsstore.application.sync;

import buncheoleasy.global.scheduler.SchedulerActivationGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매주 월요일 아침 S3 의 접수처 스냅샷을 마스터에 적용하고 배송지 지점명을 정합화한다. 크롤러가 매주 월요일
 * 04:00 에 수집·게시하므로 3시간 여유를 두고 그 결과를 같은 날 반영한다. 새 스냅샷이 없으면 diff 0건으로
 * 끝나는 멱등 실행. 크롤러 지연·수동 재수집·긴급 게시분은 다음 주기까지 대기하므로, 즉시 반영이 필요하면
 * CVS_STORE_SYNC_CRON 을 임시 조정해 재기동한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CvsStoreSyncScheduler {

  private final CvsStoreSnapshotReader cvsStoreSnapshotReader;
  private final CvsStoreSyncService cvsStoreSyncService;
  private final SchedulerActivationGate schedulerActivationGate;

  @Scheduled(cron = "${app.cvs-store.sync.cron:0 0 7 * * MON}", zone = "Asia/Seoul")
  public void syncCvsStores() {
    // cron 트리거는 initial-delay 보호가 없다 — 전환 전 인스턴스의 실행을 게이트로 차단.
    // 멱등이라 중복 실행 자체는 무해하지만, 미검증 빌드의 쓰기를 막는 정책은 동일하게 따른다.
    if (!schedulerActivationGate.isActive()) {
      log.info("기동 유예 중 — 접수처 스냅샷 동기화 건너뜀");
      return;
    }
    try {
      // S3 읽기는 트랜잭션 밖에서 — 다운로드 동안 DB 커넥션을 잡지 않는다 (apply 가 @Transactional).
      cvsStoreSnapshotReader
          .loadLatest()
          .ifPresentOrElse(
              cvsStoreSyncService::apply, () -> log.info("접수처 스냅샷 동기화 스킵 — 스냅샷 미게시"));
    } catch (final Exception e) {
      // 주 1회 배치 — 실패하면 다음 주에 재시도되고, 마스터는 마지막 성공 상태로 서비스를 계속한다.
      log.error("접수처 스냅샷 동기화 실패", e);
    }
  }
}
