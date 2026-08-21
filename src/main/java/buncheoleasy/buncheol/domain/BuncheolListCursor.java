package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;

/**
 * 공개 분철 목록(둘러보기/검색) 전용 키셋 커서.
 *
 * <p>정렬 키는 {@code (그룹 순위, 그룹 내 정렬 시각, id)} 3-튜플이다.
 *
 * <ul>
 *   <li>그룹 순위: RECRUITING·PAYMENT_COLLECTING = {@value #RANK_RECRUITING}, CONFIRMED = {@value
 *       #RANK_CONFIRMED}, CANCELLED(인원 미달) = {@value #RANK_CANCELLED}. 오름차순이라 모집중 → 진행확정 →
 *       인원미달취소 순으로 노출된다. (HOST_CANCELLED 는 목록 비노출이라 커서에 등장하지 않는다.)
 *   <li>그룹 내 정렬 시각: 모집중 그룹은 {@code createdAt}(최신 개최순), 마감·취소분은 {@code deadline}(현재와 가까운 마감순). 모두 내림차순.
 *   <li>id: 동일 시각 tie-break (내림차순).
 * </ul>
 *
 * <p>인코딩 형식: {@code <rank>_<sortAt ISO-8601>_<id>} (예: {@code 0_2026-05-21T00:00:00Z_42}). 첫 페이지는
 * {@link #firstPage()} 또는 {@code parse(null)} / {@code parse("")} 로 표현하며 모든 필드가 {@code null} 이다.
 *
 * <p>두 그룹의 정렬 컬럼이 달라(createdAt vs deadline) 인박스와 공유하는 {@link buncheoleasy.global.page.Cursor}(createdAt,id)
 * 로는 이 복합 정렬을 표현할 수 없어 별도로 둔다.
 *
 * <p><b>전이 시 보장 수준(at-least-once)</b>: 그룹 순위를 분철의 <b>현재 status</b> 로 정하므로, 페이지네이션 도중 어떤 분철이
 * RECRUITING→CONFIRMED 로 전이되면(자동 마감 스케줄러·호스트 취소) 모집중 그룹에서 이미 노출된 뒤 마감 그룹에서 한 번 더 보일 수 있다. 반대로 누락(skip)은
 * 발생하지 않는다 — 모집중에서 놓쳤어도 마감 그룹 스캔에서 반드시 잡힌다. 목록 UX 에서 중복이 누락보다 안전하므로 의도된 동작이며, 중복 제거를 위해 그룹 간 가드를 넣으면 오히려
 * 누락을 유발하니 주의한다(프론트가 같은 스크롤 세션 내 id 기준 dedup).
 */
public record BuncheolListCursor(Integer groupRank, Instant sortAt, Long id) {

  /**
   * 모집중 그룹 순위 — 항상 마감 그룹보다 먼저 노출된다.
   *
   * <p>RECRUITING 과 <b>PAYMENT_COLLECTING</b>(C2C 개최자가 성사를 확정해 입금을 수집하는 구간)이 이 그룹을 공유한다. 둘 다 아직
   * 진행 중인 분철이고 {@code createdAt} 이라는 같은 정렬 축을 쓴다. 예전에는 PAYMENT_COLLECTING 이 어느 그룹에도 없어, 개최자가 성사를
   * 확정하는 순간 분철이 목록·검색에서 통째로 사라졌다가 전원 입금확인 후 CONFIRMED 가 되어서야 다시 나타났다.
   */
  public static final int RANK_RECRUITING = 0;

  /** 진행확정(CONFIRMED) 그룹 순위. */
  public static final int RANK_CONFIRMED = 1;

  /** 인원 미달 취소(CANCELLED) 그룹 순위 — 맨 뒤. */
  public static final int RANK_CANCELLED = 2;

  private static final String DELIMITER = "_";
  private static final int EXPECTED_PARTS = 3;
  private static final int RANK_INDEX = 0;
  private static final int SORT_AT_INDEX = 1;
  private static final int ID_INDEX = 2;

  public static BuncheolListCursor firstPage() {
    return new BuncheolListCursor(null, null, null);
  }

  public static BuncheolListCursor parse(final String raw) {
    if (raw == null || raw.isBlank()) {
      return firstPage();
    }
    final String[] parts = raw.split(DELIMITER);
    if (parts.length != EXPECTED_PARTS) {
      throw new BusinessException(ErrorCode.CURSOR_INVALID);
    }
    try {
      return new BuncheolListCursor(
          Integer.parseInt(parts[RANK_INDEX]),
          Instant.parse(parts[SORT_AT_INDEX]),
          Long.parseLong(parts[ID_INDEX]));
    } catch (final RuntimeException ex) {
      throw new BusinessException(ErrorCode.CURSOR_INVALID, ex);
    }
  }

  /**
   * 직전 페이지의 마지막 분철로부터 다음 커서를 만든다. 분철의 현재 상태로 그룹 순위와 정렬 시각을 결정한다: RECRUITING·PAYMENT_COLLECTING 은
   * (rank0, createdAt), CONFIRMED 는 (rank1, deadline), CANCELLED 는 (rank2, deadline). 목록은 이 네 상태만 노출하므로 다른
   * 상태는 들어오지 않는다.
   */
  public static BuncheolListCursor from(final Buncheol buncheol) {
    return switch (buncheol.getStatus()) {
      case RECRUITING, PAYMENT_COLLECTING ->
          new BuncheolListCursor(RANK_RECRUITING, buncheol.getCreatedAt(), buncheol.getId());
      case CONFIRMED ->
          new BuncheolListCursor(RANK_CONFIRMED, buncheol.getDeadline(), buncheol.getId());
      default -> new BuncheolListCursor(RANK_CANCELLED, buncheol.getDeadline(), buncheol.getId());
    };
  }

  public boolean isFirstPage() {
    return groupRank == null;
  }

  /** 커서가 모집중 그룹을 가리키는지 여부. 첫 페이지({@code groupRank == null})는 false. */
  public boolean isRecruitingGroup() {
    return groupRank != null && groupRank == RANK_RECRUITING;
  }

  /** 호출 측은 {@link #isFirstPage()} 가 false 인 인스턴스만 넘긴다 (hasNext 분기 후 호출). */
  public String encode() {
    return groupRank + DELIMITER + sortAt + DELIMITER + id;
  }
}
