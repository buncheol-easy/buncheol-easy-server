package buncheoleasy.buncheol.infrastructure.bookmark;

import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaBuncheolBookmarkRepository extends JpaRepository<BuncheolBookmark, Long> {

  boolean existsByUserIdAndBuncheolId(Long userId, Long buncheolId);

  long deleteByUserIdAndBuncheolId(Long userId, Long buncheolId);

  List<BuncheolBookmark> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
