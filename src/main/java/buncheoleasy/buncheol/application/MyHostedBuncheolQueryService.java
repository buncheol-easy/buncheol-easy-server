package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyHostedBuncheolQueryService {

  private final BuncheolRepository buncheolRepository;
  private final BuncheolMemberRepository buncheolMemberRepository;
  private final ParticipationRepository participationRepository;
  private final GroupRepository groupRepository;

  @Transactional(readOnly = true)
  public List<MyHostedBuncheolResponse> getMyHostedBuncheols(final Long hostId) {
    List<Buncheol> buncheols = buncheolRepository.findAllByHostIdOrderByCreatedAtDesc(hostId);
    if (buncheols.isEmpty()) {
      return List.of();
    }

    List<Long> buncheolIds = buncheols.stream().map(Buncheol::getId).toList();
    List<Long> groupIds = buncheols.stream().map(Buncheol::getGroupId).distinct().toList();

    Map<Long, Long> slotCountByBuncheolId =
        buncheolMemberRepository.findAllByBuncheolIds(buncheolIds).stream()
            .collect(Collectors.groupingBy(BuncheolMember::getBuncheolId, Collectors.counting()));

    Map<Long, Long> activeCountByBuncheolId =
        participationRepository.countActiveByBuncheolIds(buncheolIds).stream()
            .collect(
                Collectors.toMap(
                    BuncheolActiveParticipationCount::buncheolId,
                    BuncheolActiveParticipationCount::count));

    Map<Long, String> groupNameById =
        groupRepository.findAllByIds(groupIds).stream()
            .collect(Collectors.toMap(Group::getId, Group::getName));

    return buncheols.stream()
        .map(b -> toResponse(b, slotCountByBuncheolId, activeCountByBuncheolId, groupNameById))
        .toList();
  }

  private MyHostedBuncheolResponse toResponse(
      final Buncheol buncheol,
      final Map<Long, Long> slotCountByBuncheolId,
      final Map<Long, Long> activeCountByBuncheolId,
      final Map<Long, String> groupNameById) {
    String groupName = groupNameById.get(buncheol.getGroupId());
    if (groupName == null) {
      throw new IllegalStateException(
          "buncheolId="
              + buncheol.getId()
              + " 의 groupId="
              + buncheol.getGroupId()
              + " 에 해당하는 그룹을 찾을 수 없습니다");
    }
    int slotCount = slotCountByBuncheolId.getOrDefault(buncheol.getId(), 0L).intValue();
    long activeCount = activeCountByBuncheolId.getOrDefault(buncheol.getId(), 0L);
    return new MyHostedBuncheolResponse(
        buncheol.getId(),
        buncheol.getTitle(),
        groupName,
        buncheol.getStatus(),
        buncheol.getDeadline(),
        slotCount,
        activeCount,
        buncheol.getCreatedAt());
  }
}
