package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyParticipationQueryService {

  private final ParticipationRepository participationRepository;
  private final BuncheolRepository buncheolRepository;
  private final BuncheolMemberRepository buncheolMemberRepository;
  private final GroupMemberRepository groupMemberRepository;

  @Transactional(readOnly = true)
  public List<MyParticipationResponse> getMyParticipations(final Long participantId) {
    List<Participation> participations =
        participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(participantId);
    if (participations.isEmpty()) {
      return List.of();
    }

    List<Long> buncheolIds =
        participations.stream().map(Participation::getBuncheolId).distinct().toList();
    Map<Long, Buncheol> buncheolById =
        buncheolRepository.findAllByIds(buncheolIds).stream()
            .collect(Collectors.toMap(Buncheol::getId, b -> b));

    // 분철 단위로 member 슬롯을 한번에 가져와 슬롯 수 집계 + 참여 슬롯 메타 조회에 모두 활용.
    List<BuncheolMember> buncheolMembers =
        buncheolMemberRepository.findAllByBuncheolIds(buncheolIds);
    Map<Long, BuncheolMember> buncheolMemberById =
        buncheolMembers.stream().collect(Collectors.toMap(BuncheolMember::getId, m -> m));
    Map<Long, Long> slotCountByBuncheolId =
        buncheolMembers.stream()
            .collect(Collectors.groupingBy(BuncheolMember::getBuncheolId, Collectors.counting()));

    // 응답에는 사용자가 실제로 참여한 슬롯의 멤버 이름만 노출되므로,
    // 분철 전체 슬롯이 아닌 참여한 슬롯의 memberId 만 조회한다.
    Set<Long> participatedBuncheolMemberIds =
        participations.stream().map(Participation::getBuncheolMemberId).collect(Collectors.toSet());
    List<Long> participatedGroupMemberIds =
        buncheolMembers.stream()
            .filter(bm -> participatedBuncheolMemberIds.contains(bm.getId()))
            .map(BuncheolMember::getMemberId)
            .distinct()
            .toList();
    Map<Long, String> groupMemberNameById =
        groupMemberRepository.findAllByIds(participatedGroupMemberIds).stream()
            .collect(Collectors.toMap(GroupMember::getId, GroupMember::getName));

    return participations.stream()
        .map(p -> toResponse(p, buncheolById, buncheolMemberById, slotCountByBuncheolId, groupMemberNameById))
        .toList();
  }

  private MyParticipationResponse toResponse(
      final Participation participation,
      final Map<Long, Buncheol> buncheolById,
      final Map<Long, BuncheolMember> buncheolMemberById,
      final Map<Long, Long> slotCountByBuncheolId,
      final Map<Long, String> groupMemberNameById) {
    Buncheol buncheol = buncheolById.get(participation.getBuncheolId());
    BuncheolMember buncheolMember = buncheolMemberById.get(participation.getBuncheolMemberId());
    int slotCount =
        slotCountByBuncheolId.getOrDefault(participation.getBuncheolId(), 0L).intValue();
    return new MyParticipationResponse(
        participation.getId(),
        participation.getBuncheolId(),
        buncheol.getTitle(),
        slotCount,
        groupMemberNameById.get(buncheolMember.getMemberId()),
        participation.getTotalAmount(),
        participation.getStatus(),
        participation.getCancelReason(),
        buncheol.getStatus(),
        buncheol.getDeadline(),
        participation.getDueAt(),
        participation.getConfirmedAt());
  }
}
