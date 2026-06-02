package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressRepository;
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
  private final ShippingAddressRepository shippingAddressRepository;

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

    // 결제 금액(제시가 + 배송비) 산정을 위해 참여별 배송지를 한 번에 조회한다 (배송수단 → 분철 배송비 정책 매핑).
    List<Long> shippingAddressIds =
        participations.stream().map(Participation::getShippingAddressId).distinct().toList();
    Map<Long, ShippingAddress> shippingAddressById =
        shippingAddressRepository.findAllByIds(shippingAddressIds).stream()
            .collect(Collectors.toMap(ShippingAddress::getId, sa -> sa));

    return participations.stream()
        .map(
            p ->
                toResponse(
                    p,
                    buncheolById,
                    buncheolMemberById,
                    slotCountByBuncheolId,
                    groupMemberNameById,
                    shippingAddressById))
        .toList();
  }

  private MyParticipationResponse toResponse(
      final Participation participation,
      final Map<Long, Buncheol> buncheolById,
      final Map<Long, BuncheolMember> buncheolMemberById,
      final Map<Long, Long> slotCountByBuncheolId,
      final Map<Long, String> groupMemberNameById,
      final Map<Long, ShippingAddress> shippingAddressById) {
    Buncheol buncheol = buncheolById.get(participation.getBuncheolId());
    BuncheolMember buncheolMember = buncheolMemberById.get(participation.getBuncheolMemberId());
    int slotCount =
        slotCountByBuncheolId.getOrDefault(participation.getBuncheolId(), 0L).intValue();
    // 참여가 존재하는 한 배송지는 FK RESTRICT 로 삭제 불가하고, 배송수단은 참여 생성 시 정책 지원이 검증되므로
    // shippingAddress non-null · feeFor 성공이 보장된다 (이 불변식이 깨지면 목록 조회 전체가 실패하니 주의).
    ShippingAddress shippingAddress = shippingAddressById.get(participation.getShippingAddressId());
    long shippingFee = buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
    long paymentAmount = participation.getBidAmount() + shippingFee;
    return new MyParticipationResponse(
        participation.getId(),
        participation.getBuncheolId(),
        buncheol.getTitle(),
        slotCount,
        groupMemberNameById.get(buncheolMember.getMemberId()),
        participation.getBidAmount(),
        shippingFee,
        paymentAmount,
        participation.getStatus(),
        buncheol.getStatus(),
        buncheol.getDeadline(),
        participation.getDueAt(),
        participation.getClosedRank());
  }
}
