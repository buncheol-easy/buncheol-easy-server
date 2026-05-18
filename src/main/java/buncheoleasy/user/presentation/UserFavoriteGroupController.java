package buncheoleasy.user.presentation;

import buncheoleasy.user.application.MyFavoriteGroupQueryService;
import buncheoleasy.user.application.UserFavoriteGroupService;
import buncheoleasy.user.dto.response.MyFavoriteGroupResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/groups")
@RequiredArgsConstructor
public class UserFavoriteGroupController {

  private final UserFavoriteGroupService userFavoriteGroupService;
  private final MyFavoriteGroupQueryService myFavoriteGroupQueryService;

  /** 최애 그룹 등록 API */
  @PostMapping("/{groupId}/favorite")
  public ResponseEntity<Void> addFavoriteGroup(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long groupId) {
    userFavoriteGroupService.addFavoriteGroup(userId, groupId);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** 최애 그룹 해제 API */
  @DeleteMapping("/{groupId}/favorite")
  public ResponseEntity<Void> removeFavoriteGroup(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long groupId) {
    userFavoriteGroupService.removeFavoriteGroup(userId, groupId);
    return ResponseEntity.noContent().build();
  }

  /** 내 최애 그룹 목록 조회 API. 최신 등록 순. */
  @GetMapping("/favorites/me")
  public ResponseEntity<List<MyFavoriteGroupResponse>> getMyFavoriteGroups(
      @AuthenticationPrincipal final Long userId) {
    return ResponseEntity.ok(myFavoriteGroupQueryService.getMyFavoriteGroups(userId));
  }
}
