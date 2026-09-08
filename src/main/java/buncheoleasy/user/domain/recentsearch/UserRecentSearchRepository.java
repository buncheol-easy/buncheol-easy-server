package buncheoleasy.user.domain.recentsearch;

import java.util.List;

public interface UserRecentSearchRepository {

  UserRecentSearch save(UserRecentSearch search);

  /** 사용자의 최근 검색 이력을 최신순(created_at DESC, tie-breaker id DESC)으로 최대 7개 조회한다. */
  List<UserRecentSearch> findTop7ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  /** 동일 (userId, keyword) 행을 모두 삭제하고 삭제된 row 개수를 반환한다. dedupe 용. */
  int deleteByUserIdAndKeyword(Long userId, String keyword);

  /** 사용자의 최신순 정렬에서 {@code keep} 개째 이후 행들의 id 를 반환한다. 7개 초과 정리용. */
  List<Long> findIdsToTrim(Long userId, int keep);

  /** 주어진 id 들의 행을 일괄 삭제한다. 빈 리스트 호출 가능. */
  void deleteAllByIdIn(List<Long> ids);

  /**
   * 본인 소유 행 1건 삭제(사용자의 X 버튼). userId 를 조건에 함께 걸어 남의 id 로는 0행 —
   * 존재 여부가 응답으로 새지 않게 0행도 성공으로 다룬다(멱등).
   */
  int deleteOwnedById(Long userId, Long id);
}
