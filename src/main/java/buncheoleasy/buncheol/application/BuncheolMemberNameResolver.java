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
import java.util.Set;
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

  /** 분철별 전체 멤버 이름과 아직 안 팔린 멤버 이름. */
  public record MemberNames(Map<Long, List<String>> all, Map<Long, List<String>> available) {
    private static MemberNames empty() {
      return new MemberNames(Map.of(), Map.of());
    }
  }

  /** 분철별 전체 멤버 이름. */
  public Map<Long, List<String>> findNamesByBuncheolIds(final List<Long> buncheolIds) {
    return resolveNames(buncheolIds, Set.of()).all();
  }

  /**
   * 분철별 전체 멤버 이름과 "아직 안 팔린"(활성 참여가 없는) 멤버 이름을 한 번의 조회로 함께 만든다. {@code takenBuncheolMemberIds} 는 활성
   * 참여가 점유한 멤버 슬롯({@link BuncheolMember#getId()}) 집합이며, available 은 여기 포함되지 않은 슬롯만 모은다. available 은 all 의
   * 부분집합이라 별도 조회 없이 같은 패스에서 함께 만든다.
   */
  public MemberNames resolveNames(
      final List<Long> buncheolIds, final Set<Long> takenBuncheolMemberIds) {
    if (buncheolIds.isEmpty()) {
      return MemberNames.empty();
    }
    final List<BuncheolMember> buncheolMembers =
        buncheolMemberRepository.findAllByBuncheolIds(buncheolIds);
    if (buncheolMembers.isEmpty()) {
      return MemberNames.empty();
    }

    final List<Long> groupMemberIds =
        buncheolMembers.stream().map(BuncheolMember::getMemberId).distinct().toList();
    final Map<Long, String> nameByGroupMemberId =
        groupMemberRepository.findAllByIds(groupMemberIds).stream()
            .collect(Collectors.toMap(GroupMember::getId, GroupMember::getName));

    final Map<Long, List<String>> all = new HashMap<>();
    final Map<Long, List<String>> available = new HashMap<>();
    buncheolMembers.stream()
        .sorted(Comparator.comparing(BuncheolMember::getId))
        .forEach(
            bm -> {
              final String name = nameByGroupMemberId.get(bm.getMemberId());
              if (name == null) {
                return;
              }
              all.computeIfAbsent(bm.getBuncheolId(), k -> new ArrayList<>()).add(name);
              if (!takenBuncheolMemberIds.contains(bm.getId())) {
                available.computeIfAbsent(bm.getBuncheolId(), k -> new ArrayList<>()).add(name);
              }
            });
    return new MemberNames(all, available);
  }
}
