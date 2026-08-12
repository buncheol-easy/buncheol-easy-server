package buncheoleasy.group.application;

import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.global.query.SearchText;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.dto.response.GroupDetailResponse;
import buncheoleasy.group.dto.response.GroupMemberResponse;
import buncheoleasy.group.dto.response.GroupResponse;
import buncheoleasy.group.dto.response.GroupWithMembersResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

  private static final Duration POPULAR_WINDOW = Duration.ofDays(30);
  private static final int POPULAR_LIMIT = 5;

  private final GroupDomainService groupDomainService;
  // 인기 아티스트 집계는 buncheol 모듈이 source. application 레이어에서 두 도메인을 조합한다.
  private final BuncheolRepository buncheolRepository;
  private final GroupRepository groupRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<GroupResponse> searchGroups(final String keyword) {
    // "미입력 = 전체" 와 "정규화 후 남는 글자가 없음 = 0건" 을 구분한다. 둘을 묶어 null 로 넘기면
    // 리포지토리의 `:keyword IS NULL` 게이트가 열려 "---" 같은 입력이 전체 그룹을 반환한다.
    if (keyword == null || keyword.isBlank()) {
      return toGroupResponses(groupDomainService.searchGroups(null));
    }

    final String normalizedKeyword = SearchText.normalizeForLike(keyword);

    if (normalizedKeyword == null) {
      return List.of();
    }

    return toGroupResponses(groupDomainService.searchGroups(normalizedKeyword));
  }

  private static List<GroupResponse> toGroupResponses(final List<Group> groups) {
    return groups.stream().map(GroupResponse::from).toList();
  }

  /** 아티스트 페이지(그룹 단위 브라우즈) 헤더용 — 그룹 본문 + 멤버 전체 + 모집중 분철 수. */
  @Transactional(readOnly = true)
  public GroupDetailResponse getGroupDetail(final Long groupId) {
    final Group group = groupDomainService.getGroup(groupId);
    final List<GroupMemberResponse> members =
        groupDomainService.getGroupMembers(groupId).stream()
            .map(GroupMemberResponse::from)
            .toList();
    return GroupDetailResponse.of(
        group, members, buncheolRepository.countRecruitingByGroupId(groupId));
  }

  @Transactional(readOnly = true)
  public List<GroupMemberResponse> getGroupMembers(final Long groupId) {
    groupDomainService.validateGroupExists(groupId);
    return groupDomainService.getGroupMembers(groupId).stream()
        .map(GroupMemberResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<GroupWithMembersResponse> searchGroupsByMemberName(final String keyword) {
    final String normalizedKeyword = SearchText.normalizeForLike(keyword);

    // 정규화 후 남는 글자가 없으면 조회할 필요가 없다. null 을 그대로 넘기면 H2 는
    // CONCAT('%', NULL, '%') 를 '%%' 로 접어 전체 멤버를 반환한다 (MySQL 은 0건).
    if (normalizedKeyword == null) {
      return List.of();
    }

    List<GroupMember> matched = groupDomainService.findMembersByName(normalizedKeyword);
    if (matched.isEmpty()) {
      return List.of();
    }

    List<Long> groupIds = matched.stream().map(GroupMember::getGroupId).distinct().toList();

    Map<Long, List<GroupMemberResponse>> membersByGroupId =
        groupDomainService.findMembersInGroups(groupIds).stream()
            .collect(
                Collectors.groupingBy(
                    GroupMember::getGroupId,
                    Collectors.mapping(GroupMemberResponse::from, Collectors.toList())));

    return groupDomainService.findGroupsByIds(groupIds).stream()
        .map(group -> toGroupWithMembers(group, membersByGroupId))
        .toList();
  }

  private GroupWithMembersResponse toGroupWithMembers(
      final Group group, final Map<Long, List<GroupMemberResponse>> membersByGroupId) {
    return GroupWithMembersResponse.of(
        group, membersByGroupId.getOrDefault(group.getId(), List.of()));
  }

  /** 최근 30일간 분철 등록 수 기준 인기 그룹 상위 5개를 인기도 내림차순으로 반환한다. CANCELLED 분철은 제외. */
  @Transactional(readOnly = true)
  public List<GroupResponse> getPopularGroups() {
    final Instant since = Instant.now(clock).minus(POPULAR_WINDOW);
    final List<Long> orderedGroupIds =
        buncheolRepository.findGroupIdsByBuncheolCountSince(since, POPULAR_LIMIT);
    if (orderedGroupIds.isEmpty()) {
      return List.of();
    }
    final Map<Long, Group> groupById =
        groupRepository.findAllByIds(orderedGroupIds).stream()
            .collect(Collectors.toMap(Group::getId, g -> g));
    return orderedGroupIds.stream().map(groupById::get).map(GroupResponse::from).toList();
  }
}
