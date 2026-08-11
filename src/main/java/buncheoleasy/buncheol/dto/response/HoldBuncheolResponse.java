package buncheoleasy.buncheol.dto.response;

/**
 * 분철 개최(생성) 응답. 과거엔 {@code ResponseEntity<Void>} 라 FE 가 생성 직후 개최 목록을 다시 불러 제목·그룹명 매칭으로 새 분철을 찾아야
 * 했고, 동일 제목으로 재개최하면 잘못된 분철을 집을 수 있었다 (docs/53 Q-15).
 */
public record HoldBuncheolResponse(Long buncheolId) {

  public static HoldBuncheolResponse of(final Long buncheolId) {
    return new HoldBuncheolResponse(buncheolId);
  }
}
