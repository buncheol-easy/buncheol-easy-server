package buncheoleasy.user.presentation;

import buncheoleasy.user.application.recentsearch.UserRecentSearchCommandService;
import buncheoleasy.user.application.recentsearch.UserRecentSearchQueryService;
import buncheoleasy.user.dto.response.RecentSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/search-keywords")
@RequiredArgsConstructor
public class SearchKeywordController {

  private final UserRecentSearchQueryService userRecentSearchQueryService;
  private final UserRecentSearchCommandService userRecentSearchCommandService;

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

  /**
   * 최근 검색어 1건 삭제(알약의 X). 응답의 {@code id} 로 지운다 — 키워드 매칭은 인코딩·중복 행에서
   * 어긋난다. 남의 id·이미 지워진 id 도 204 — 존재 여부를 응답으로 흘리지 않는 멱등 삭제.
   */
  @DeleteMapping("/recent/{searchId}")
  public ResponseEntity<Void> deleteRecent(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long searchId) {
    userRecentSearchCommandService.delete(userId, searchId);
    return ResponseEntity.noContent().build();
  }
}
