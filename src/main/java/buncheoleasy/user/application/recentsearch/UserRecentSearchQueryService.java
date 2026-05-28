package buncheoleasy.user.application.recentsearch;

import buncheoleasy.user.domain.recentsearch.UserRecentSearchRepository;
import buncheoleasy.user.dto.response.RecentSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검색창에 노출할 사용자별 최근 검색 이력을 조회한다. 비로그인은 빈 리스트. */
@Service
@RequiredArgsConstructor
public class UserRecentSearchQueryService {

  private final UserRecentSearchRepository userRecentSearchRepository;

  @Transactional(readOnly = true)
  public List<RecentSearchResponse> getRecent(final Long userId) {
    if (userId == null) {
      return List.of();
    }
    return userRecentSearchRepository.findTop7ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
        .map(RecentSearchResponse::from)
        .toList();
  }
}
