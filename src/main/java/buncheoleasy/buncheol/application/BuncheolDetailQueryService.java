package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberDetailResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationItemResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationSummaryResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolDetailQueryService {

  private final BuncheolRepository buncheolRepository;
  private final BuncheolImageRepository buncheolImageRepository;
  private final BuncheolMemberRepository buncheolMemberRepository;
  private final ParticipationRepository participationRepository;
  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;

  @Transactional(readOnly = true)
  public BuncheolDetailResponse getDetail(final Long buncheolId, final Long userId) {
    Buncheol buncheol =
        buncheolRepository
            .findById(buncheolId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

    // 개최자가 취소한(HOST_CANCELLED) 분철은 목록뿐 아니라 상세에서도 숨긴다(존재하지 않는 것처럼 404).
    if (buncheol.getStatus() == BuncheolStatus.HOST_CANCELLED) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND);
    }

    Group group =
        groupRepository
            .findById(buncheol.getGroupId())
            .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

    List<String> imageUrls =
        buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId).stream()
            .map(BuncheolImage::getImageUrl)
            .toList();

    List<BuncheolMember> buncheolMembers =
        buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);

    Map<Long, GroupMember> groupMemberByGroupMemberId =
        buncheolMembers.isEmpty()
            ? Map.of()
            : groupMemberRepository
                .findAllByGroupIdAndIds(
                    buncheol.getGroupId(),
                    buncheolMembers.stream().map(BuncheolMember::getMemberId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(GroupMember::getId, Function.identity()));

    List<Participation> activeParticipations =
        participationRepository.findActiveByBuncheolId(buncheolId);
    // 멤버 슬롯당 활성 참여는 최대 1건(선착순)이므로, 활성 참여가 존재하는 멤버 슬롯은 '마감'으로 표시한다.
    Set<Long> takenMemberIds =
        activeParticipations.stream()
            .map(Participation::getBuncheolMemberId)
            .collect(Collectors.toSet());
    int confirmedCount =
        (int)
            activeParticipations.stream()
                .filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED)
                .count();

    List<BuncheolMemberDetailResponse> memberResponses =
        buncheolMembers.stream()
            .map(bm -> toMemberDetail(bm, groupMemberByGroupMemberId, takenMemberIds))
            .toList();

    List<ShippingOptionResponse> shippingOptions =
        toShippingOptions(buncheol.getShippingFeePolicy());
    MyParticipationSummaryResponse myParticipation =
        userId == null ? null : toMyParticipation(userId, activeParticipations);
    boolean hostedByMe = userId != null && buncheol.isHost(userId);

    return new BuncheolDetailResponse(
        buncheol.getId(),
        buncheol.getTitle(),
        group.getName(),
        buncheol.getPurchaseSite(),
        buncheol.getDeadline(),
        buncheol.getDescription(),
        buncheol.getStatus(),
        buncheol.getMinHeadcount(),
        confirmedCount,
        imageUrls,
        shippingOptions,
        memberResponses,
        hostedByMe,
        myParticipation);
  }

  private BuncheolMemberDetailResponse toMemberDetail(
      final BuncheolMember buncheolMember,
      final Map<Long, GroupMember> groupMemberByGroupMemberId,
      final Set<Long> takenMemberIds) {
    GroupMember groupMember = groupMemberByGroupMemberId.get(buncheolMember.getMemberId());
    return new BuncheolMemberDetailResponse(
        buncheolMember.getId(),
        buncheolMember.getMemberId(),
        groupMember == null ? null : groupMember.getName(),
        groupMember == null ? null : groupMember.getImage(),
        buncheolMember.getPrice(),
        !takenMemberIds.contains(buncheolMember.getId()));
  }

  private List<ShippingOptionResponse> toShippingOptions(final ShippingFeePolicy policy) {
    List<ShippingOptionResponse> options = new ArrayList<>(2);
    if (policy.gs25ShippingFee() != null) {
      options.add(new ShippingOptionResponse(ShippingMethod.GS25_HALF, policy.gs25ShippingFee()));
    }
    if (policy.cuShippingFee() != null) {
      options.add(new ShippingOptionResponse(ShippingMethod.CU_HALF, policy.cuShippingFee()));
    }
    return options;
  }

  private MyParticipationSummaryResponse toMyParticipation(
      final Long userId, final List<Participation> activeParticipations) {
    List<MyParticipationItemResponse> items =
        activeParticipations.stream()
            .filter(p -> userId.equals(p.getParticipantId()))
            .map(
                p ->
                    new MyParticipationItemResponse(
                        p.getId(), p.getBuncheolMemberId(), p.getStatus()))
            .toList();
    return new MyParticipationSummaryResponse(items.size(), items);
  }
}
