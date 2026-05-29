package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolManagementOptionResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
import buncheoleasy.buncheol.dto.response.WinnerDeliveryResponse;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개최자 분철 관리 화면 조회 서비스. 호스트 본인 권한을 검증한 뒤, 분철·옵션·참여·낙찰자 배송 정보를 단일 응답으로 조립한다.
 *
 * <p>{@link ParticipationRepository#findActiveByBuncheolId(Long)} 는 ACTIVE_BID/AWAITING_PAYMENT/
 * CONFIRMED 셋을 모두 {@code bidAmount DESC, id ASC} 로 가져오므로 옵션별 최고가·낙찰자 후보를 메모리에서 그룹핑한다.
 */
@Service
@RequiredArgsConstructor
public class BuncheolManagementQueryService {

  private final BuncheolRepository buncheolRepository;
  private final BuncheolMemberRepository buncheolMemberRepository;
  private final ParticipationRepository participationRepository;
  private final DeliveryRepository deliveryRepository;
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

    Map<Long, GroupMember> groupMemberById =
        buncheolMembers.isEmpty()
            ? Map.of()
            : groupMemberRepository
                .findAllByGroupIdAndIds(
                    buncheol.getGroupId(),
                    buncheolMembers.stream().map(BuncheolMember::getMemberId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(GroupMember::getId, Function.identity()));

    List<Participation> participations =
        participationRepository.findActiveByBuncheolId(buncheolId);
    Map<Long, List<Participation>> participationsByMember =
        participations.stream()
            .collect(
                Collectors.groupingBy(
                    Participation::getBuncheolMemberId, Collectors.toUnmodifiableList()));

    List<Long> confirmedParticipationIds =
        participations.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED)
            .map(Participation::getId)
            .toList();
    Map<Long, Delivery> deliveryByParticipationId =
        deliveryRepository.findAllByParticipationIds(confirmedParticipationIds).stream()
            .collect(Collectors.toMap(Delivery::getParticipationId, Function.identity()));

    List<BuncheolManagementOptionResponse> options =
        buncheolMembers.stream()
            .map(bm -> toOption(bm, groupMemberById, participationsByMember, deliveryByParticipationId))
            .toList();

    return new BuncheolManagementResponse(
        buncheol.getId(),
        buncheol.getTitle(),
        group.getName(),
        buncheol.getPurchaseSite(),
        buncheol.getStatus(),
        buncheol.getDeadline(),
        buncheolMembers.size(),
        participations.size(),
        options);
  }

  private BuncheolManagementOptionResponse toOption(
      final BuncheolMember buncheolMember,
      final Map<Long, GroupMember> groupMemberById,
      final Map<Long, List<Participation>> participationsByMember,
      final Map<Long, Delivery> deliveryByParticipationId) {
    GroupMember groupMember = groupMemberById.get(buncheolMember.getMemberId());
    List<Participation> bids = participationsByMember.getOrDefault(buncheolMember.getId(), List.of());

    // 입력은 bidAmount DESC, id ASC 정렬이므로 첫 원소가 상태 무관 최고가다.
    Long currentHighestBid = bids.isEmpty() ? null : bids.get(0).getBidAmount();

    // 슬롯당 CONFIRMED 는 도메인 불변식상 최대 1건이다.
    WinnerDeliveryResponse winner =
        bids.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED)
            .findFirst()
            .map(confirmed -> toWinner(deliveryByParticipationId.get(confirmed.getId())))
            .orElse(null);

    return new BuncheolManagementOptionResponse(
        buncheolMember.getId(),
        buncheolMember.getMemberId(),
        groupMember == null ? null : groupMember.getName(),
        groupMember == null ? null : groupMember.getImage(),
        bids.size(),
        currentHighestBid,
        winner);
  }

  private WinnerDeliveryResponse toWinner(final Delivery delivery) {
    if (delivery == null) {
      return null;
    }
    return new WinnerDeliveryResponse(
        delivery.getId(),
        delivery.getShippingMethod(),
        delivery.getStoreName(),
        delivery.getReceiverNickname(),
        delivery.getReceiverPhoneNumber(),
        delivery.getTrackingNumber(),
        delivery.getStatus());
  }
}
