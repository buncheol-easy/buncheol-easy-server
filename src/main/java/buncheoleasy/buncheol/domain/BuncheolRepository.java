package buncheoleasy.buncheol.domain;

import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.global.page.Cursor;
import java.util.List;
import java.util.Optional;

public interface BuncheolRepository {

  Buncheol save(Buncheol buncheol);

  Optional<Buncheol> findById(Long id);

  List<Buncheol> findAllByIds(List<Long> ids);

  /**
   * 호스트의 분철 중 사용자에게 노출 가능한 항목을 {@code createdAt DESC} 정렬로 조회한다. 취소된({@link
   * BuncheolStatus#CANCELLED}) 분철은 결과에서 제외한다.
   */
  List<Buncheol> findVisibleByHostIdOrderByCreatedAtDesc(Long hostId);

  /**
   * 활성 분철(CANCELLED 제외) 중 검색 조건에 부합하는 항목을 {@code createdAt DESC, id DESC} 정렬로 최대 {@code limit} 개
   * 조회한다.
   *
   * <p>hasNext 판별을 위해 호출 측은 보통 {@code size + 1} 을 {@code limit} 으로 넘긴다.
   */
  List<Buncheol> search(BuncheolSearchCondition condition, Cursor cursor, int limit);

  boolean existsActiveByHostId(Long hostId);
}
