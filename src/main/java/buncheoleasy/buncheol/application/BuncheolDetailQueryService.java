package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberBidResponse;
import buncheoleasy.buncheol.dto.response.MyBidResponse;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolDetailQueryService {

  private static final int TOP_BIDS_LIMIT = 3;

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
    // bidAmount DESC, id ASC 정렬된 입력을 유지한 채 멤버별로 그룹핑한다.
    Map<Long, List<Participation>> participationsByMember =
        activeParticipations.stream()
            .collect(
                Collectors.groupingBy(
                    Participation::getBuncheolMemberId, Collectors.toUnmodifiableList()));

    List<BuncheolMemberBidResponse> memberResponses =
        buncheolMembers.stream()
            .map(bm -> toMemberBid(bm, groupMemberByGroupMemberId, participationsByMember))
            .toList();

    List<ShippingOptionResponse> shippingOptions =
        toShippingOptions(buncheol.getShippingFeePolicy());
    MyParticipationSummaryResponse myParticipation =
        userId == null ? null : toMyParticipation(userId, buncheolMembers, participationsByMember);

    return new BuncheolDetailResponse(
        buncheol.getId(),
        buncheol.getTitle(),
        group.getName(),
        buncheol.getPurchaseSite(),
        buncheol.getDeadline(),
        buncheol.getDescription(),
        buncheol.getStatus(),
        imageUrls,
        shippingOptions,
        memberResponses,
        myParticipation);
  }

  private BuncheolMemberBidResponse toMemberBid(
      final BuncheolMember buncheolMember,
      final Map<Long, GroupMember> groupMemberByGroupMemberId,
      final Map<Long, List<Participation>> participationsByMember) {
    GroupMember groupMember = groupMemberByGroupMemberId.get(buncheolMember.getMemberId());
    List<Participation> bids =
        participationsByMember.getOrDefault(buncheolMember.getId(), List.of());
    List<Long> topBidAmounts =
        bids.stream().limit(TOP_BIDS_LIMIT).map(Participation::getBidAmount).toList();
    return new BuncheolMemberBidResponse(
        buncheolMember.getId(),
        buncheolMember.getMemberId(),
        groupMember == null ? null : groupMember.getName(),
        groupMember == null ? null : groupMember.getImage(),
        topBidAmounts,
        bids.size());
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

  /**
   * 슬롯 등록 순(buncheolMembers ASC) 으로 순회하며 내 입찰을 1건씩 수집한다. 도메인 규칙상 한 슬롯에 동일 유저의 활성 참여는 최대 1건이므로
   * {@code participatedMemberCount == myBids.size()} 가 항상 성립한다.
   */
  private MyParticipationSummaryResponse toMyParticipation(
      final Long userId,
      final List<BuncheolMember> buncheolMembers,
      final Map<Long, List<Participation>> participationsByMember) {
    List<MyBidResponse> myBids = new ArrayList<>();
    for (BuncheolMember bm : buncheolMembers) {
      List<Participation> bids = participationsByMember.getOrDefault(bm.getId(), List.of());
      for (int i = 0; i < bids.size(); i++) {
        Participation p = bids.get(i);
        if (userId.equals(p.getParticipantId())) {
          myBids.add(new MyBidResponse(p.getId(), bm.getId(), p.getBidAmount(), i + 1));
        }
      }
    }
    return new MyParticipationSummaryResponse(myBids.size(), myBids);
  }
}
