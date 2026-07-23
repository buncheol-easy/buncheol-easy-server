package buncheoleasy.admin.dto.response;

import java.util.List;

/**
 * 관리자 벌크 처리 결과. 항목별로 독립 처리하므로 일부 실패가 나머지 성공을 되돌리지 않는다 — 프론트는 {@code failures} 를 보고 실패 건만 재시도하거나
 * 사유(code)를 안내한다.
 */
public record AdminBulkResultResponse(List<Long> succeededIds, List<Failure> failures) {

  /** 실패 1건. {@code code}/{@code message} 는 {@code ErrorCode} 의 값이다. */
  public record Failure(Long id, String code, String message) {}
}
