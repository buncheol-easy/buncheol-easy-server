package buncheoleasy.group.application;

import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMember;
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
    return groupDomainService.searchGroups(keyword).stream().map(GroupResponse::from).toList();
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
    List<GroupMember> matched = groupDomainService.findMembersByName(keyword);
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
