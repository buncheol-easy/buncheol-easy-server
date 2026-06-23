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
 *   <li>그룹 순위: RECRUITING = {@value #RANK_RECRUITING}, CONFIRMED = {@value #RANK_CONFIRMED}. 오름차순이라 모집중이
 *       먼저 노출되고 마감분이 뒤를 잇는다.
 *   <li>그룹 내 정렬 시각: 모집중은 {@code createdAt}(최신 개최순), 마감분은 {@code deadline}(현재와 가까운 마감순). 두 그룹 모두 내림차순.
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

  /** 모집중(RECRUITING) 그룹 순위 — 항상 마감 그룹보다 먼저 노출된다. */
  public static final int RANK_RECRUITING = 0;

  /** 마감(CONFIRMED) 그룹 순위. */
  public static final int RANK_CONFIRMED = 1;

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
   * 직전 페이지의 마지막 분철로부터 다음 커서를 만든다. 분철의 현재 상태로 그룹 순위와 정렬 시각을 결정하므로, 상태가 RECRUITING 이면 createdAt 을, 그 외(마감)면
   * deadline 을 정렬 시각으로 싣는다.
   */
  public static BuncheolListCursor from(final Buncheol buncheol) {
    if (buncheol.getStatus() == BuncheolStatus.RECRUITING) {
      return new BuncheolListCursor(RANK_RECRUITING, buncheol.getCreatedAt(), buncheol.getId());
    }
    return new BuncheolListCursor(RANK_CONFIRMED, buncheol.getDeadline(), buncheol.getId());
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
