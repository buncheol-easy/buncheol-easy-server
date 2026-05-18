package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolBookmarkService;
import buncheoleasy.buncheol.application.MyBookmarkedBuncheolQueryService;
import buncheoleasy.buncheol.dto.request.BookmarkSortOption;
import buncheoleasy.buncheol.dto.response.MyBookmarkedBuncheolResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/buncheols")
@RequiredArgsConstructor
public class BuncheolBookmarkController {

  private final BuncheolBookmarkService buncheolBookmarkService;
  private final MyBookmarkedBuncheolQueryService myBookmarkedBuncheolQueryService;

  /** 분철 찜 등록 API */
  @PostMapping("/{buncheolId}/bookmark")
  public ResponseEntity<Void> addBookmark(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long buncheolId) {
    buncheolBookmarkService.addBookmark(userId, buncheolId);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** 분철 찜 해제 API */
  @DeleteMapping("/{buncheolId}/bookmark")
  public ResponseEntity<Void> removeBookmark(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long buncheolId) {
    buncheolBookmarkService.removeBookmark(userId, buncheolId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 마이페이지 - 내가 찜한 분철 목록 조회 API.
   *
   * <p>sort: LATEST(기본, 찜 등록 시각 내림차순) | DEADLINE(분철 마감 임박순). hideClosed=true 면 RECRUITING 상태가
   * 아닌 분철(CLOSED/PAID/SETTLING/FINISHED/CANCELLED) 은 결과에서 제외한다.
   */
  @GetMapping("/bookmarks/me")
  public ResponseEntity<List<MyBookmarkedBuncheolResponse>> getMyBookmarkedBuncheols(
      @AuthenticationPrincipal final Long userId,
      @RequestParam(defaultValue = "LATEST") final BookmarkSortOption sort,
      @RequestParam(defaultValue = "false") final boolean hideClosed) {
    return ResponseEntity.ok(
        myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(userId, sort, hideClosed));
  }
}
