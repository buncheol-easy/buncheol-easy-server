package buncheoleasy.cvsstore.application.sync;

import buncheoleasy.global.scheduler.SchedulerActivationGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 S3 의 접수처 스냅샷을 마스터에 적용하고 배송지 지점명을 정합화한다. 크롤러의 수집·게시 주기(주 1회
 * cron)와 독립적으로 돌며, 새 스냅샷이 없으면 diff 0건으로 끝나는 멱등 실행이라 매일 돌아도 비용이 없다 —
 * 수동 재수집·긴급 게시도 다음 날 자동 반영된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CvsStoreSyncScheduler {

  private final CvsStoreSnapshotReader cvsStoreSnapshotReader;
  private final CvsStoreSyncService cvsStoreSyncService;
  private final SchedulerActivationGate schedulerActivationGate;

  @Scheduled(cron = "${app.cvs-store.sync.cron:0 30 5 * * *}", zone = "Asia/Seoul")
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
      // 하루 1회 배치 — 실패해도 다음 날 재시도되고, 마스터는 마지막 성공 상태로 서비스를 계속한다.
      log.error("접수처 스냅샷 동기화 실패", e);
    }
  }
}
