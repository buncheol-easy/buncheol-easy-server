package buncheoleasy.user.application.recentsearch;

import buncheoleasy.user.domain.recentsearch.UserRecentSearch;
import buncheoleasy.user.domain.recentsearch.UserRecentSearchRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 검색 이력의 dedupe-save-trim 시퀀스를 트랜잭션 안에서 수행한다.
 *
 * <p>동일 keyword 에 대한 unique 제약은 두지 않는다. 동시 검색 시 일시적으로 중복 행이 생길 수 있으나, 다음 검색 시점의 dedupe DELETE 가 모두
 * 정리하므로 self-healing. 사용자 체감 영향은 패널을 같은 순간에 새로고침하지 않는 한 거의 없다.
 */
@Service
@RequiredArgsConstructor
public class UserRecentSearchCommandService {

  static final int MAX_KEEP = 7;

  private final UserRecentSearchRepository repository;

  @Transactional
  public void record(final Long userId, final String keyword) {
    repository.deleteByUserIdAndKeyword(userId, keyword);
    repository.save(UserRecentSearch.create(userId, keyword));
    trim(userId);
  }

  private void trim(final Long userId) {
    final List<Long> ids = repository.findIdsToTrim(userId, MAX_KEEP);
    repository.deleteAllByIdIn(ids);
  }
}
