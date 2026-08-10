package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolManagementParticipantResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
import buncheoleasy.buncheol.dto.response.ManagementDeliveryResponse;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자(개최자) 분철 관리 화면 조회 서비스. 호스트 본인 권한을 검증한 뒤, 분철 정보와 활성 참여자(입금확인 대상·확정 참여) 목록을 환불계좌·배송 스냅샷과 함께 단일
 * 응답으로 조립한다. 입금확인·환불은 운영자가 이 화면을 보고 처리한다.
 */
@Service
@RequiredArgsConstructor
public class BuncheolManagementQueryService {

  private final BuncheolRepository buncheolRepository;
  private final BuncheolMemberRepository buncheolMemberRepository;
  private final ParticipationRepository participationRepository;
  private final DeliveryRepository deliveryRepository;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;

  @Transactional(readOnly = true)
  public BuncheolManagementResponse getManagement(final Long buncheolId, final Long hostId) {
    Buncheol buncheol =
        buncheolRepository
            .findById(buncheolId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));
    buncheol.validateOwner(hostId);

    Group group =
        groupRepository
            .findById(buncheol.getGroupId())
            .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

    List<BuncheolMember> buncheolMembers =
        buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);
    Map<Long, String> memberNameBySlotId =
        resolveMemberNames(buncheol.getGroupId(), buncheolMembers);

    List<Participation> participations = participationRepository.findActiveByBuncheolId(buncheolId);

    List<Long> confirmedParticipationIds =
        participations.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED)
            .map(Participation::getId)
            .toList();
    Map<Long, Delivery> deliveryByParticipationId =
        deliveryRepository.findAllByParticipationIds(confirmedParticipationIds).stream()
            .collect(Collectors.toMap(Delivery::getParticipationId, Function.identity()));

    Map<Long, User> userById =
        userRepository
            .findAllByIds(participations.stream().map(Participation::getParticipantId).toList())
            .stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    List<BuncheolManagementParticipantResponse> participants =
        participations.stream()
            .map(p -> toParticipant(p, memberNameBySlotId, userById, deliveryByParticipationId))
            .toList();

    return new BuncheolManagementResponse(
        buncheol.getId(),
        buncheol.getTitle(),
        group.getName(),
        buncheol.getPurchaseSite(),
        buncheol.getStatus(),
        buncheol.getDeadline(),
        buncheol.getMinHeadcount(),
        buncheolMembers.size(),
        confirmedParticipationIds.size(),
        participants,
        buncheol.getFlowType(),
        buncheol.getPaymentDueAt());
  }

  // 멤버 슬롯 id → 그룹 멤버명. 멤버 슬롯 → 그룹 멤버 2단계로 해석한다. (group_members 누락 시 null 허용을 위해 HashMap 사용)
  private Map<Long, String> resolveMemberNames(
      final Long groupId, final List<BuncheolMember> buncheolMembers) {
    if (buncheolMembers.isEmpty()) {
      return Map.of();
    }
    List<Long> memberIds =
        buncheolMembers.stream().map(BuncheolMember::getMemberId).distinct().toList();
    Map<Long, GroupMember> groupMemberById =
        groupMemberRepository.findAllByGroupIdAndIds(groupId, memberIds).stream()
            .collect(Collectors.toMap(GroupMember::getId, Function.identity()));
    Map<Long, String> nameBySlotId = new HashMap<>();
    for (BuncheolMember buncheolMember : buncheolMembers) {
      GroupMember groupMember = groupMemberById.get(buncheolMember.getMemberId());
      nameBySlotId.put(buncheolMember.getId(), groupMember == null ? null : groupMember.getName());
    }
    return nameBySlotId;
  }

  private BuncheolManagementParticipantResponse toParticipant(
      final Participation participation,
      final Map<Long, String> memberNameBySlotId,
      final Map<Long, User> userById,
      final Map<Long, Delivery> deliveryByParticipationId) {
    User participant = userById.get(participation.getParticipantId());
    Delivery delivery = deliveryByParticipationId.get(participation.getId());
    return new BuncheolManagementParticipantResponse(
        participation.getId(),
        participant == null ? null : participant.getNickname().value(),
        participation.getBuncheolMemberId(),
        memberNameBySlotId.get(participation.getBuncheolMemberId()),
        participation.getTotalAmount(),
        participation.getStatus(),
        participation.getDueAt(),
        participation.getConfirmedAt(),
        RefundAccountResponse.from(participation.getRefundAccount()),
        delivery == null ? null : ManagementDeliveryResponse.from(delivery),
        participation.getPaymentSentAt());
  }
}
