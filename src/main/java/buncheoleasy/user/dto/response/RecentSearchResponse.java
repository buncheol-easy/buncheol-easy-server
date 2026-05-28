package buncheoleasy.user.dto.response;

import buncheoleasy.user.domain.recentsearch.UserRecentSearch;

/** 검색창에 노출할 최근 검색 이력 1건. */
public record RecentSearchResponse(Long id, String keyword) {

  public static RecentSearchResponse from(final UserRecentSearch search) {
    return new RecentSearchResponse(search.getId(), search.getKeyword());
  }
}
