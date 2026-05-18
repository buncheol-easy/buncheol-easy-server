package buncheoleasy.buncheol.domain.bookmark;

import java.util.List;

public interface BuncheolBookmarkRepository {

  /**
   * 찜을 저장한다. (user_id, buncheol_id) unique 제약 위반 시 BusinessException 으로 변환된다 (어댑터
   * 책임).
   */
  BuncheolBookmark save(BuncheolBookmark bookmark);

  boolean existsByUserIdAndBuncheolId(Long userId, Long buncheolId);

  /** 동일 (userId, buncheolId) 행을 삭제하고 삭제된 row 개수를 반환한다. 0 이면 해제할 찜이 없었다는 의미. */
  int deleteByUserIdAndBuncheolId(Long userId, Long buncheolId);

  /** 사용자의 찜 목록을 최신순(created_at DESC, tie-breaker id DESC) 으로 조회한다. */
  List<BuncheolBookmark> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
