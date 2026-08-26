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
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자(개최자) 분철 관리 화면 조회 서비스. 호스트 본인 권한을 검증한 뒤, 분철 정보와 활성 참여자(입금확인 대상·확정 참여) 목록을 환불계좌·배송 스냅샷과 함께 단일
 * 응답으로 조립한다. 입금확인·환불은 운영자가 이 화면을 보고 처리한다. 취소된 참여는 슬롯을 점유하지 않아 참여자 목록과 분리해 담는다.
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
    // 취소되면 활성 조회에서 빠지는데, 개최자가 환불하려면 계좌에 닿아야 한다 (C2C 는 개최자가 환불 주체).
    List<Participation> cancelled = participationRepository.findCancelledByBuncheolId(buncheolId);

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
            .findAllByIds(
                Stream.concat(participations.stream(), cancelled.stream())
                    .map(Participation::getParticipantId)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    List<BuncheolManagementParticipantResponse> participants =
        participations.stream()
            .map(p -> toParticipant(p, memberNameBySlotId, userById, deliveryByParticipationId))
            .toList();
    // 배송 스냅샷은 취소 cascade 에서 삭제되므로 취소분에는 조회하지 않는다.
    List<BuncheolManagementParticipantResponse> cancelledParticipants =
        cancelled.stream()
            .map(p -> toParticipant(p, memberNameBySlotId, userById, Map.of()))
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
        cancelledParticipants,
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
        participation.getRefundAccount().holder(),
        participation.getTotalAmount(),
        participation.getShippingFee(),
        participation.getStatus(),
        participation.getDueAt(),
        participation.getConfirmedAt(),
        refundAccountFor(participation),
        delivery == null ? null : ManagementDeliveryResponse.from(delivery),
        participation.getPaymentSentAt());
  }

  /**
   * 개최자에게 내려줄 환불 계좌 (docs/70 결정 21). 통장 대조에 필요한 것은 입금자명뿐이라 평시에는 계좌를 내리지 않는다. 계좌번호가 필요한
   * 유일한 상황은 <b>개최자가 직접 환불해야 하는 건</b>이고, 그건 취소분 중 입금 흔적이 남은 건뿐이다.
   *
   * <p>판정 키({@code paymentSentAt} 또는 {@code confirmedAt})는 개최 관리 화면의 "환불이 필요한 참여" 목록 필터와 같은
   * 기준이다 — 둘이 갈리면 목록에는 뜨는데 계좌가 비는 행이 생긴다.
   */
  private static RefundAccountResponse refundAccountFor(final Participation participation) {
    return needsHostRefund(participation)
        ? RefundAccountResponse.from(participation.getRefundAccount())
        : null;
  }

  private static boolean needsHostRefund(final Participation participation) {
    return participation.getStatus() == ParticipationStatus.CANCELLED
        && (participation.getPaymentSentAt() != null || participation.getConfirmedAt() != null);
  }
}
