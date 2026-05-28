package buncheoleasy.user.presentation;

import buncheoleasy.user.application.recentsearch.UserRecentSearchQueryService;
import buncheoleasy.user.dto.response.RecentSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/search-keywords")
@RequiredArgsConstructor
public class SearchKeywordController {

  private final UserRecentSearchQueryService userRecentSearchQueryService;

  /**
   * 검색창에 띄울 최근 검색어 최대 7개 조회 (비로그인 허용).
   *
   * <p>비로그인 호출 시 익명 principal(문자열) 은 {@code Long} 캐스팅에 실패해 {@code userId} 가 null 로 들어와 빈 리스트를 반환한다.
   */
  @GetMapping("/recent")
  public ResponseEntity<List<RecentSearchResponse>> getRecent(
      @AuthenticationPrincipal final Long userId) {
    return ResponseEntity.ok(userRecentSearchQueryService.getRecent(userId));
  }
}
