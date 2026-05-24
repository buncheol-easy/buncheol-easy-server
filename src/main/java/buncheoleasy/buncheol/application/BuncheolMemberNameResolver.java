package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 분철 ID 목록에 대해 각 분철의 멤버 이름 리스트를 한 번에 조회한다. BuncheolMember 와 GroupMember 를 IN batch 로 가져온 뒤
 * application 에서 그룹핑·정렬한다. 정렬 기준은 {@link BuncheolMember#getId()} ASC (호스트가 등록한 슬롯 순).
 */
@Component
@RequiredArgsConstructor
public class BuncheolMemberNameResolver {

  private final BuncheolMemberRepository buncheolMemberRepository;
  private final GroupMemberRepository groupMemberRepository;

  public Map<Long, List<String>> findNamesByBuncheolIds(final List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return Map.of();
    }
    final List<BuncheolMember> buncheolMembers =
        buncheolMemberRepository.findAllByBuncheolIds(buncheolIds);
    if (buncheolMembers.isEmpty()) {
      return Map.of();
    }

    final List<Long> groupMemberIds =
        buncheolMembers.stream().map(BuncheolMember::getMemberId).distinct().toList();
    final Map<Long, String> nameByGroupMemberId =
        groupMemberRepository.findAllByIds(groupMemberIds).stream()
            .collect(Collectors.toMap(GroupMember::getId, GroupMember::getName));

    final Map<Long, List<String>> result = new HashMap<>();
    buncheolMembers.stream()
        .sorted(Comparator.comparing(BuncheolMember::getId))
        .forEach(
            bm -> {
              final String name = nameByGroupMemberId.get(bm.getMemberId());
              if (name == null) {
                return;
              }
              result.computeIfAbsent(bm.getBuncheolId(), k -> new ArrayList<>()).add(name);
            });
    return result;
  }
}
