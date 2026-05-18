package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolBookmarkService {

  private final BuncheolBookmarkRepository buncheolBookmarkRepository;
  private final BuncheolDomainService buncheolDomainService;

  @Transactional
  public void addBookmark(final Long userId, final Long buncheolId) {
    // 분철 존재 검증 (상태 무관 — 종료/취소된 분철도 찜 가능). 없으면 BUNCHEOL_NOT_FOUND.
    buncheolDomainService.getBuncheol(buncheolId);

    if (buncheolBookmarkRepository.existsByUserIdAndBuncheolId(userId, buncheolId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_BOOKMARK_ALREADY_EXISTS);
    }

    buncheolBookmarkRepository.save(BuncheolBookmark.create(userId, buncheolId));
  }

  @Transactional
  public void removeBookmark(final Long userId, final Long buncheolId) {
    int deleted = buncheolBookmarkRepository.deleteByUserIdAndBuncheolId(userId, buncheolId);
    if (deleted == 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_BOOKMARK_NOT_FOUND);
    }
  }
}
