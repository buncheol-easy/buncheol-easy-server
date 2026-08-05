package buncheoleasy.cvsstore.application.sync;

import java.util.Optional;

public interface CvsStoreSnapshotReader {

  /** 최신 스냅샷을 읽는다. 아직 게시된 적이 없으면 {@code Optional.empty()}. */
  Optional<CvsStoreSnapshot> loadLatest();
}
