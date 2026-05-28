package buncheoleasy.user.infrastructure.recentsearch;

import buncheoleasy.user.domain.recentsearch.UserRecentSearch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaUserRecentSearchRepository extends JpaRepository<UserRecentSearch, Long> {

  List<UserRecentSearch> findTop7ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  long deleteByUserIdAndKeyword(Long userId, String keyword);

  // self-healing 정상 상태에선 0~1개. LIMIT 1000 은 일관성 깨진 사용자도 한 번에 정리할 안전 상한.
  @Query(
      value =
          "SELECT id FROM user_recent_searches "
              + "WHERE user_id = :userId "
              + "ORDER BY created_at DESC, id DESC "
              + "LIMIT 1000 OFFSET :keep",
      nativeQuery = true)
  List<Long> findIdsToTrim(@Param("userId") Long userId, @Param("keep") int keep);
}
