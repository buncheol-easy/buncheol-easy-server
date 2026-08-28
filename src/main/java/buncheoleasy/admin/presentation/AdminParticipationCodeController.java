package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminParticipationCodeService;
import buncheoleasy.admin.dto.request.AdminParticipationCodeIssueRequest;
import buncheoleasy.admin.dto.request.AdminMemberAccessTypeRequest;
import buncheoleasy.admin.dto.response.AdminBuncheolMemberResponse;
import buncheoleasy.admin.dto.response.AdminParticipationCodeResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 참여 코드 발급 API. {@code /v1/admin/**} 은 SecurityConfig 가 ROLE_ADMIN 을 강제한다. */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminParticipationCodeController {

  private final AdminParticipationCodeService adminParticipationCodeService;

  /** 발급 화면용 멤버 목록 (코드 발급 대상 슬롯). */
  @GetMapping("/buncheols/{buncheolId}/members")
  public ResponseEntity<List<AdminBuncheolMemberResponse>> getBuncheolMembers(
      @PathVariable final Long buncheolId) {
    return ResponseEntity.ok(adminParticipationCodeService.getBuncheolMembers(buncheolId));
  }

  /** 발급 이력 전체 (최신순). */
  @GetMapping("/buncheols/{buncheolId}/participation-codes")
  public ResponseEntity<List<AdminParticipationCodeResponse>> getCodes(
      @PathVariable final Long buncheolId) {
    return ResponseEntity.ok(adminParticipationCodeService.getCodes(buncheolId));
  }

  /** {@code reissue=true} 면 이전 코드를 폐기하고 새로 발급한다. */
  @PostMapping("/buncheols/{buncheolId}/participation-codes")
  public ResponseEntity<AdminParticipationCodeResponse> issue(
      @PathVariable final Long buncheolId,
      @Valid @RequestBody final AdminParticipationCodeIssueRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminParticipationCodeService.issue(buncheolId, request));
  }

  /** 멤버 접근 정책 전환. 활성 참여가 있으면 바꿀 수 없다. */
  @PatchMapping("/buncheols/{buncheolId}/members/{buncheolMemberId}")
  public ResponseEntity<Void> changeBuncheolMemberAccessType(
      @PathVariable final Long buncheolId,
      @PathVariable final Long buncheolMemberId,
      @Valid @RequestBody final AdminMemberAccessTypeRequest request) {
    adminParticipationCodeService.changeBuncheolMemberAccessType(
        buncheolId, buncheolMemberId, request.accessType());
    return ResponseEntity.noContent().build();
  }

  /** 코드 폐기 (유출 신고 등). */
  @DeleteMapping("/participation-codes/{codeId}")
  public ResponseEntity<Void> revoke(@PathVariable final Long codeId) {
    adminParticipationCodeService.revoke(codeId);
    return ResponseEntity.noContent().build();
  }
}
