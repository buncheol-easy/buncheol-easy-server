package buncheoleasy.group.presentation;

import buncheoleasy.group.application.GroupService;
import buncheoleasy.group.dto.response.GroupMemberResponse;
import buncheoleasy.group.dto.response.GroupResponse;
import buncheoleasy.group.dto.response.GroupWithMembersResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/groups")
@RequiredArgsConstructor
@Validated
public class GroupController {

  private final GroupService groupService;

  @GetMapping
  public ResponseEntity<List<GroupResponse>> searchGroups(
      @RequestParam(required = false) final String keyword) {
    return ResponseEntity.ok(groupService.searchGroups(keyword));
  }

  @GetMapping("/popular")
  public ResponseEntity<List<GroupResponse>> getPopularGroups() {
    return ResponseEntity.ok(groupService.getPopularGroups());
  }

  @GetMapping("/members")
  public ResponseEntity<List<GroupWithMembersResponse>> searchGroupsByMemberName(
      @RequestParam @NotBlank @Size(max = 100) final String keyword) {
    return ResponseEntity.ok(groupService.searchGroupsByMemberName(keyword));
  }

  @GetMapping("/{groupId}/members")
  public ResponseEntity<List<GroupMemberResponse>> getGroupMembers(
      @PathVariable final Long groupId) {
    return ResponseEntity.ok(groupService.getGroupMembers(groupId));
  }
}
