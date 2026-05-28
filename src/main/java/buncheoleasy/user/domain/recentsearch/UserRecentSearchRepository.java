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
}
