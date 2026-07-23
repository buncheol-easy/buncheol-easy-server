package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberSaleStatus;
import buncheoleasy.buncheol.dto.response.MyParticipationItemResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationSummaryResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
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
    // 멤버 슬롯당 활성 참여는 최대 1건(선착순)이므로, 슬롯별 활성 참여로 판매 상태·입금 기한을 계산한다.
    Map<Long, Participation> activeByMemberId =
        activeParticipations.stream()
            .collect(Collectors.toMap(Participation::getBuncheolMemberId, Function.identity()));
    int confirmedCount =
        (int)
            activeParticipations.stream()
                .filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED)
                .count();

    List<BuncheolMemberDetailResponse> memberResponses =
        buncheolMembers.stream()
            .map(bm -> toMemberDetail(bm, groupMemberByGroupMemberId, activeByMemberId, userId))
            .toList();

    List<ShippingOptionResponse> shippingOptions =
        ShippingOptionResponse.listFrom(buncheol.getShippingFeePolicy());
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
      final Map<Long, Participation> activeByMemberId,
      final Long userId) {
    GroupMember groupMember = groupMemberByGroupMemberId.get(buncheolMember.getMemberId());
    Participation active = activeByMemberId.get(buncheolMember.getId());
    BuncheolMemberSaleStatus saleStatus = toSaleStatus(active);
    return new BuncheolMemberDetailResponse(
        buncheolMember.getId(),
        buncheolMember.getMemberId(),
        groupMember == null ? null : groupMember.getName(),
        groupMember == null ? null : groupMember.getImage(),
        buncheolMember.getPrice(),
        saleStatus,
        saleStatus == BuncheolMemberSaleStatus.AWAITING_PAYMENT ? active.getDueAt() : null,
        // AVAILABLE(공석) 슬롯은 점유 참여가 없으므로 항상 false — saleStatus 와의 불변식을 코드로 보장한다.
        saleStatus != BuncheolMemberSaleStatus.AVAILABLE
            && userId != null
            && userId.equals(active.getParticipantId()));
  }

  // exhaustive switch: ParticipationStatus 에 상태가 추가되면 컴파일 에러로 매핑 누락을 잡는다.
  private BuncheolMemberSaleStatus toSaleStatus(final Participation active) {
    if (active == null) {
      return BuncheolMemberSaleStatus.AVAILABLE;
    }
    return switch (active.getStatus()) {
      case AWAITING_PAYMENT -> BuncheolMemberSaleStatus.AWAITING_PAYMENT;
      case CONFIRMED -> BuncheolMemberSaleStatus.SOLD;
      // 취소된 참여는 슬롯을 점유하지 않는다 (활성 참여만 조회하므로 실제로는 위 두 상태만 온다).
      case CANCELLED -> BuncheolMemberSaleStatus.AVAILABLE;
    };
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
