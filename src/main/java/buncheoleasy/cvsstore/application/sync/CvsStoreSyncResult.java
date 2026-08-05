package buncheoleasy.cvsstore.application.sync;

/** 스냅샷 동기화 1회 실행의 요약. {@code applied == false} 면 {@code skipReason} 에 사유가 담긴다. */
public record CvsStoreSyncResult(
    boolean applied,
    int inserted,
    int updated,
    int deleted,
    int renamed,
    int renameConflicts,
    int closedCandidates,
    String skipReason) {

  public static CvsStoreSyncResult skipped(final String reason) {
    return new CvsStoreSyncResult(false, 0, 0, 0, 0, 0, 0, reason);
  }
}
