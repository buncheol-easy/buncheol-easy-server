package buncheoleasy.user.infrastructure.recentsearch;

import buncheoleasy.user.domain.recentsearch.UserRecentSearch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaUserRecentSearchRepository extends JpaRepository<UserRecentSearch, Long> {

  List<UserRecentSearch> findTop7ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  long deleteByUserIdAndKeyword(Long userId, String keyword);

  // 파생 delete 는 SELECT 후 em.remove 2단계라, 같은 id 삭제가 겹치면(더블탭) 늦은 쪽이
  // StaleStateException(500)으로 죽어 멱등 계약이 깨진다. 단일 DELETE 문은 0행이 그냥 0.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM UserRecentSearch s WHERE s.userId = :userId AND s.id = :id")
  int deleteOwnedById(@Param("userId") Long userId, @Param("id") Long id);

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
