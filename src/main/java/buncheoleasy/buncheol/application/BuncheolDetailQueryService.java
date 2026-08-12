package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolImageResponse;
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

    // 이미지는 등록 순(id ASC = 업로드 순) 그대로 내려주고, 대표사진은 순서가 아니라 항목별 thumbnail 플래그로 식별한다.
    List<BuncheolImageResponse> images =
        BuncheolImageResponse.listFrom(
            buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId));

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

    boolean openForNewParticipation = isOpenForNewParticipation(buncheol);
    List<BuncheolMemberDetailResponse> memberResponses =
        buncheolMembers.stream()
            .map(
                bm ->
                    toMemberDetail(
                        bm,
                        groupMemberByGroupMemberId,
                        activeByMemberId,
                        openForNewParticipation,
                        userId))
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
        images,
        shippingOptions,
        memberResponses,
        hostedByMe,
        myParticipation,
        buncheol.getFlowType(),
        buncheol.getPaymentDueAt(),
        buncheol.getOpenChatUrl());
  }

  /**
   * 분철이 지금 빈 슬롯에 신규 참여를 받는지 — 빈 슬롯의 판매 상태를 AVAILABLE(신청 가능) 로 내릴지 CLOSED(마감) 로 내릴지의 기준이다 (docs/53
   * Q-14). 참여 생성 가드({@code ParticipationService#participate})와 같은 상태 집합을 본다: LEGACY 는 모집중일 때만, C2C 는
   * 성사 확정 후 입금 수집중(PAYMENT_COLLECTING) 구간의 추가 모집까지 허용된다 (docs/46 §4.7-E1).
   *
   * <p>마감 시각(deadline) 경과로 인한 차단은 여기서 보지 않는다 — 마감 후 모집중 구간은 별도 표시 정합 항목이라 이번 범위 밖이다.
   */
  private boolean isOpenForNewParticipation(final Buncheol buncheol) {
    return switch (buncheol.getStatus()) {
      case RECRUITING -> true;
      case PAYMENT_COLLECTING -> buncheol.isC2c();
      // CONFIRMED·CANCELLED 는 신청이 409(BCH-060) 로 막힌다. HOST_CANCELLED 는 위에서 404 라 여기까지 오지 않는다.
      case CONFIRMED, CANCELLED, HOST_CANCELLED -> false;
    };
  }

  private BuncheolMemberDetailResponse toMemberDetail(
      final BuncheolMember buncheolMember,
      final Map<Long, GroupMember> groupMemberByGroupMemberId,
      final Map<Long, Participation> activeByMemberId,
      final boolean openForNewParticipation,
      final Long userId) {
    GroupMember groupMember = groupMemberByGroupMemberId.get(buncheolMember.getMemberId());
    Participation active = activeByMemberId.get(buncheolMember.getId());
    BuncheolMemberSaleStatus saleStatus = toSaleStatus(active, openForNewParticipation);
    return new BuncheolMemberDetailResponse(
        buncheolMember.getId(),
        buncheolMember.getMemberId(),
        groupMember == null ? null : groupMember.getName(),
        groupMember == null ? null : groupMember.getImage(),
        buncheolMember.getPrice(),
        saleStatus,
        saleStatus == BuncheolMemberSaleStatus.AWAITING_PAYMENT ? active.getDueAt() : null,
        // 점유 참여가 없는 슬롯(AVAILABLE·CLOSED)은 항상 false — active 로 직접 판정해 NPE 여지를 없앤다.
        active != null && userId != null && userId.equals(active.getParticipantId()));
  }

  // exhaustive switch: ParticipationStatus 에 상태가 추가되면 컴파일 에러로 매핑 누락을 잡는다.
  private BuncheolMemberSaleStatus toSaleStatus(
      final Participation active, final boolean openForNewParticipation) {
    if (active == null) {
      return emptySlotStatus(openForNewParticipation);
    }
    return switch (active.getStatus()) {
      case APPLIED -> BuncheolMemberSaleStatus.APPLIED;
      case AWAITING_PAYMENT -> BuncheolMemberSaleStatus.AWAITING_PAYMENT;
      // "보냈어요" 마킹도 외부 관점에선 점유+입금 미확정 — 단 만료 면제라 dueAt 카운트다운은 노출하지 않는다.
      case PAYMENT_SENT -> BuncheolMemberSaleStatus.AWAITING_PAYMENT;
      case CONFIRMED -> BuncheolMemberSaleStatus.SOLD;
      // 취소된 참여는 슬롯을 점유하지 않는다 (활성 참여만 조회하므로 실제로는 위 상태들만 온다).
      case CANCELLED -> emptySlotStatus(openForNewParticipation);
    };
  }

  // 공석의 판매 상태 — 신규 참여를 받지 않는 분철에서는 신청 가능한 것처럼 보이면 안 된다 (docs/53 Q-14).
  private BuncheolMemberSaleStatus emptySlotStatus(final boolean openForNewParticipation) {
    return openForNewParticipation
        ? BuncheolMemberSaleStatus.AVAILABLE
        : BuncheolMemberSaleStatus.CLOSED;
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
