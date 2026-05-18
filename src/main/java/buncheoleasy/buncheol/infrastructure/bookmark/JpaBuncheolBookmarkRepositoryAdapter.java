package buncheoleasy.buncheol.infrastructure.bookmark;

import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBuncheolBookmarkRepositoryAdapter implements BuncheolBookmarkRepository {

  private final JpaBuncheolBookmarkRepository jpaBuncheolBookmarkRepository;

  @Override
  public BuncheolBookmark save(final BuncheolBookmark bookmark) {
    try {
      return jpaBuncheolBookmarkRepository.save(bookmark);
    } catch (DataIntegrityViolationException ex) {
      // unique(user_id, buncheol_id) 제약 위반 — 동시 요청으로 사전 체크 우회 시 후방어
      throw new BusinessException(ErrorCode.BUNCHEOL_BOOKMARK_ALREADY_EXISTS);
    }
  }

  @Override
  public boolean existsByUserIdAndBuncheolId(final Long userId, final Long buncheolId) {
    return jpaBuncheolBookmarkRepository.existsByUserIdAndBuncheolId(userId, buncheolId);
  }

  @Override
  public int deleteByUserIdAndBuncheolId(final Long userId, final Long buncheolId) {
    return (int) jpaBuncheolBookmarkRepository.deleteByUserIdAndBuncheolId(userId, buncheolId);
  }

  @Override
  public List<BuncheolBookmark> findAllByUserIdOrderByCreatedAtDescIdDesc(final Long userId) {
    return jpaBuncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
  }
}
