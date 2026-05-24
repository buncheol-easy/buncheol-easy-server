package buncheoleasy.buncheol.infrastructure.bookmark;

import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaBuncheolBookmarkRepository extends JpaRepository<BuncheolBookmark, Long> {

  boolean existsByUserIdAndBuncheolId(Long userId, Long buncheolId);

  long deleteByUserIdAndBuncheolId(Long userId, Long buncheolId);

  List<BuncheolBookmark> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  @Query(
      "SELECT bk.buncheolId FROM BuncheolBookmark bk "
          + "WHERE bk.userId = :userId AND bk.buncheolId IN :buncheolIds")
  List<Long> findBookmarkedBuncheolIds(
      @Param("userId") Long userId, @Param("buncheolIds") List<Long> buncheolIds);
}
