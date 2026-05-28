package buncheoleasy.user.infrastructure.recentsearch;

import buncheoleasy.user.domain.recentsearch.UserRecentSearch;
import buncheoleasy.user.domain.recentsearch.UserRecentSearchRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserRecentSearchRepositoryAdapter implements UserRecentSearchRepository {

  private final JpaUserRecentSearchRepository jpaUserRecentSearchRepository;

  @Override
  public UserRecentSearch save(final UserRecentSearch search) {
    return jpaUserRecentSearchRepository.save(search);
  }

  @Override
  public List<UserRecentSearch> findTop7ByUserIdOrderByCreatedAtDescIdDesc(final Long userId) {
    return jpaUserRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(userId);
  }

  @Override
  public int deleteByUserIdAndKeyword(final Long userId, final String keyword) {
    return (int) jpaUserRecentSearchRepository.deleteByUserIdAndKeyword(userId, keyword);
  }

  @Override
  public List<Long> findIdsToTrim(final Long userId, final int keep) {
    return jpaUserRecentSearchRepository.findIdsToTrim(userId, keep);
  }

  @Override
  public void deleteAllByIdIn(final List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }
    jpaUserRecentSearchRepository.deleteAllByIdInBatch(ids);
  }
}
