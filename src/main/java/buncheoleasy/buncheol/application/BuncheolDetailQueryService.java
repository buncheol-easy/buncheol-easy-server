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
import java.time.Clock;
import java.time.Instant;
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
  private final Clock clock;

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

    // 공석 슬롯을 "신청 가능"으로 내릴지의 기준 — 참여 가드와 같은 도메인 술어를 쓴다 (docs/53 Q-14).
    boolean openForNewParticipation = buncheol.acceptsNewParticipation(Instant.now(clock));
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
    // 내 활성 참여 — 요약 응답과 링크 노출 판정이 같은 목록을 본다. 판정 기준을 두 곳에 복사하면 갈린다.
    // CANCELLED 조건은 정상 경로에선 도달하지 않는다(findActiveByBuncheolId 가 이미 거른다). 조회가 바뀌어도
    // 게이트가 버티도록 남겨 둔다 — 아래 isMine 이 같은 이유로 같은 방어를 갖고 있다.
    List<Participation> myActiveParticipations =
        userId == null
            ? List.of()
            : activeParticipations.stream()
                .filter(
                    p ->
                        p.getStatus() != ParticipationStatus.CANCELLED
                            && userId.equals(p.getParticipantId()))
                .toList();
    MyParticipationSummaryResponse myParticipation =
        userId == null ? null : toMyParticipation(myActiveParticipations);
    boolean hostedByMe = userId != null && buncheol.isHost(userId);

    // 오픈채팅 링크는 개최자·활성 참여자에게만 싣는다. 이 조회는 비로그인도 열려 있어서
    // (SecurityConfig PUBLIC_GET_PATHS) 무조건 실으면 목록을 훑어 전 분철의 채팅방 링크를 모을 수 있다.
    //
    // ⚠️ 이건 차단이 아니라 <b>수집 비용을 올리는</b> 조치다 — 신청(APPLIED)은 무입금 슬롯 선점이고 즉시 자발
    // 취소가 되므로, 공석이 있는 분철이라면 신청 → 링크 취득 → 취소 로 여전히 모을 수 있다. 다만 참여 기록이
    // 남아 추적되고 슬롯을 점유하며 모집이 닫힌 분철에는 아예 못 들어간다. 신청 스팸 자체의 레이트 리밋은 별건.
    //
    // 신청 단계부터 여는 것은 사용자 결정이다 — 성사 확정 전에도 개최자에게 물어볼 일이 생긴다.
    //
    // ⚠️ myParticipation 의 null 여부로 판정하면 안 된다 — toMyParticipation 은 참여가 없어도 count=0 인
    // 객체를 돌려주므로, 로그인만 하면 링크가 보이게 된다.
    String visibleOpenChatUrl =
        hostedByMe || !myActiveParticipations.isEmpty() ? buncheol.getOpenChatUrl() : null;

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
        visibleOpenChatUrl);
  }

  private BuncheolMemberDetailResponse toMemberDetail(
      final BuncheolMember buncheolMember,
      final Map<Long, GroupMember> groupMemberByGroupMemberId,
      final Map<Long, Participation> activeByMemberId,
      final boolean openForNewParticipation,
      final Long userId) {
    GroupMember groupMember = groupMemberByGroupMemberId.get(buncheolMember.getMemberId());
    Participation active = activeByMemberId.get(buncheolMember.getId());
    BuncheolMemberSaleStatus saleStatus =
        toSaleStatus(active, openForNewParticipation, buncheolMember.requiresCode());
    return new BuncheolMemberDetailResponse(
        buncheolMember.getId(),
        buncheolMember.getMemberId(),
        groupMember == null ? null : groupMember.getName(),
        groupMember == null ? null : groupMember.getImage(),
        buncheolMember.getPrice(),
        saleStatus,
        saleStatus == BuncheolMemberSaleStatus.AWAITING_PAYMENT ? active.getDueAt() : null,
        // 슬롯을 점유한 참여가 있을 때만 true. 취소 참여를 제외해 "공석인데 내 참여" 조합이 생기지 않게 한다
        // (findActiveByBuncheolId 가 활성만 주므로 실제로는 도달하지 않는 방어 조건).
        isMine(active, userId));
  }

  private boolean isMine(final Participation active, final Long userId) {
    return active != null
        && active.getStatus() != ParticipationStatus.CANCELLED
        && userId != null
        && userId.equals(active.getParticipantId());
  }

  // exhaustive switch: ParticipationStatus 에 상태가 추가되면 컴파일 에러로 매핑 누락을 잡는다.
  private BuncheolMemberSaleStatus toSaleStatus(
      final Participation active,
      final boolean openForNewParticipation,
      final boolean requiresCode) {
    if (active == null) {
      return emptySlotStatus(openForNewParticipation, requiresCode);
    }
    return switch (active.getStatus()) {
      case APPLIED -> BuncheolMemberSaleStatus.APPLIED;
      case AWAITING_PAYMENT -> BuncheolMemberSaleStatus.AWAITING_PAYMENT;
      // "보냈어요" 마킹도 외부 관점에선 점유+입금 미확정 — 단 만료 면제라 dueAt 카운트다운은 노출하지 않는다.
      case PAYMENT_SENT -> BuncheolMemberSaleStatus.AWAITING_PAYMENT;
      case CONFIRMED -> BuncheolMemberSaleStatus.SOLD;
      // 취소된 참여는 슬롯을 점유하지 않는다 (활성 참여만 조회하므로 실제로는 위 상태들만 온다).
      case CANCELLED -> emptySlotStatus(openForNewParticipation, requiresCode);
    };
  }

  /**
   * 공석의 판매 상태 — 신규 참여를 받지 않는 분철에서는 신청 가능한 것처럼 보이면 안 된다 (docs/53 Q-14). 마감을 코드 배정보다
   * 먼저 보는 이유는, 닫힌 분철에서는 코드가 있어도 참여 INSERT 가 막히기 때문이다.
   */
  private BuncheolMemberSaleStatus emptySlotStatus(
      final boolean openForNewParticipation, final boolean requiresCode) {
    if (!openForNewParticipation) {
      return BuncheolMemberSaleStatus.CLOSED;
    }
    return requiresCode
        ? BuncheolMemberSaleStatus.CODE_ONLY
        : BuncheolMemberSaleStatus.AVAILABLE;
  }

  private MyParticipationSummaryResponse toMyParticipation(
      final List<Participation> myActiveParticipations) {
    List<MyParticipationItemResponse> items =
        myActiveParticipations.stream()
            .map(
                p ->
                    new MyParticipationItemResponse(
                        p.getId(), p.getBuncheolMemberId(), p.getStatus()))
            .toList();
    return new MyParticipationSummaryResponse(items.size(), items);
  }
}
