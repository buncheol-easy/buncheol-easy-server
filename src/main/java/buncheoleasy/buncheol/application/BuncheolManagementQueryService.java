package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.BundleReleasability;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
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
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자(개최자) 분철 관리 화면 조회 서비스. 호스트 본인 권한을 검증한 뒤, 분철 정보와 활성 참여자(입금확인 대상·확정 참여) 목록을 입금자명·배송 스냅샷과 함께
 * 단일 응답으로 조립한다. 입금확인·환불은 운영자가 이 화면을 보고 처리한다. 취소된 참여는 슬롯을 점유하지 않아 참여자 목록과 분리해 담는다.
 *
 * <p><b>계좌는 평시에 내려가지 않는다</b> — 통장 대조에 필요한 것은 입금자명뿐이라 활성 참여에는 {@code depositorName} 만 붙고,
 * 계좌번호는 개최자가 실제로 환불해야 하는 건(취소분 중 입금 흔적이 있는 것)에만 채운다 ({@link #refundAccountFor}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuncheolManagementQueryService {

  private final BuncheolRepository buncheolRepository;
  private final BuncheolMemberRepository buncheolMemberRepository;
  private final ParticipationRepository participationRepository;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final Clock clock;
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

    List<Participation> confirmed =
        participations.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED)
            .toList();
    // 택배 1개 = 묶음 1개 — 다슬롯 묶음은 배송 1건을 슬롯들이 공유하므로 묶음 id 로 찾는다.
    // (참여 id 로 찾으면 배송을 갖지 않은 두 번째 슬롯이 개최 관리에서 "미발송" 으로 보인다.)
    Map<Long, Delivery> deliveryByBundleId =
        deliveryRepository
            .findAllByBundleIds(
                confirmed.stream()
                    .map(Participation::getBundleId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(Delivery::getBundleId, Function.identity()));

    Map<Long, User> userById =
        userRepository
            .findAllByIds(
                Stream.concat(participations.stream(), cancelled.stream())
                    .map(Participation::getParticipantId)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    // 계좌·입금자명의 정본은 묶음이다 (P2-c). 참여마다 읽으면 N+1 이라 한 번에 채운다.
    List<Participation> allParticipations =
        Stream.concat(participations.stream(), cancelled.stream()).toList();
    Map<Long, ParticipationBundle> bundleById =
        participationBundleDomainService.findAllByParticipations(allParticipations);
    // 배송비도 정본이 묶음이다. 활성분과 취소분을 함께 넘겨야 "활성이 하나도 없는 묶음"까지 판정된다.
    // 이 목록은 그 분철의 전 상태(active ∪ CANCELLED)라 묶음별 슬롯이 빠짐없이 들어온다 — ofAllSlots 의 전제.
    ShippingFeeAttribution shippingFees =
        ShippingFeeAttribution.ofAllSlots(allParticipations, bundleById);
    // 「제외」 가부는 묶음 단위 판정이다. 슬롯마다 다시 계산하면 같은 묶음의 행끼리 답이 갈릴 수 있으므로
    // 묶음별로 한 번만 구해 재사용한다 — 게이트와 같은 판정을 내려줘야 "버튼은 있는데 409" 가 안 생긴다.
    final Instant now = Instant.now(clock);
    Map<Long, List<Participation>> slotsByBundleId =
        allParticipations.stream()
            .filter(p -> p.getBundleId() != null)
            .collect(Collectors.groupingBy(Participation::getBundleId));
    Map<Long, BundleReleasability> releasabilityByBundleId = new HashMap<>();
    bundleById.forEach(
        (id, bundle) ->
            releasabilityByBundleId.put(
                id, BundleReleasability.of(bundle, slotsByBundleId.getOrDefault(id, List.of()), now)));
    // 0원 슬롯의 계좌 노출 판정이 플로우별로 다르다 ({@link #depositorNameOf}).
    boolean c2c = buncheol.isC2c();
    List<BuncheolManagementParticipantResponse> participants =
        participations.stream()
            .map(
                p ->
                    toParticipant(
                        p,
                        memberNameBySlotId,
                        userById,
                        deliveryByBundleId,
                        bundleById,
                        shippingFees,
                        releasabilityByBundleId,
                        c2c))
            .toList();
    // 배송 스냅샷은 취소 cascade 에서 삭제되므로 취소분에는 조회하지 않는다.
    List<BuncheolManagementParticipantResponse> cancelledParticipants =
        cancelled.stream()
            .map(
                p ->
                    toParticipant(
                        p,
                        memberNameBySlotId,
                        userById,
                        Map.of(),
                        bundleById,
                        shippingFees,
                        releasabilityByBundleId,
                        c2c))
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
        confirmed.size(),
        participants,
        cancelledParticipants,
        buncheol.getFlowType(),
        buncheol.getPaymentDueAt(),
        buncheol.getOpenChatUrl());
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
      final Map<Long, Delivery> deliveryByBundleId,
      final Map<Long, ParticipationBundle> bundleById,
      final ShippingFeeAttribution shippingFees,
      final Map<Long, BundleReleasability> releasabilityByBundleId,
      final boolean c2c) {
    User participant = userById.get(participation.getParticipantId());
    // 미연결 참여(배포선 창)는 묶음이 없다 — 계좌 없이 내려가고 클라가 닉네임으로 폴백한다.
    RefundAccount refundAccount =
        ParticipationBundleDomainService.refundAccountOf(bundleById, participation);
    // 🔴 <b>입금확인된 슬롯만</b> 배송을 문다. 배송은 이제 묶음에 붙어 있어(택배 1개 = 묶음 1개) 같은
    // 묶음의 미입금 슬롯도 키가 맞는데, 그대로 물리면 <b>입금하지도 않은 슬롯에 "배송중" 과 운송장</b>이
    // 뜬다. 한 묶음에 확정·미확정이 섞이는 건 실제로 도달 가능하다 — 슬롯 단위 입금확인
    // ({@code ParticipationService#confirmPayment})과 어드민 벌크 확인(건별 트랜잭션 순회)이 열려 있다.
    //
    // ⚠️ 맵을 조회하기 전에 null 도 걸러야 한다 — 취소분 렌더링은 Map.of() 를 넘기는데, 불변 맵은
    // null 키 조회에서 NPE 다. (ShippingFeeAttribution 과 같은 함정)
    Delivery delivery =
        participation.getStatus() == ParticipationStatus.CONFIRMED
                && participation.getBundleId() != null
            ? deliveryByBundleId.get(participation.getBundleId())
            : null;
    // 🔴 0원 판정과 표시 금액이 같은 숫자를 본다. 저장값으로 판정하면 배송비를 지던 형제 슬롯이 취소돼
    // 이 슬롯이 배송비를 이어받은 경우, 낼 돈이 있는데 「무료」로 잡혀 입금자명·환불 계좌가 빈다.
    long paymentAmount = shippingFees.totalAmountOf(participation);
    return new BuncheolManagementParticipantResponse(
        participation.getId(),
        participation.getBundleId(),
        participation.getParticipantId(),
        participant == null ? null : participant.getNickname().value(),
        participation.getBuncheolMemberId(),
        memberNameBySlotId.get(participation.getBuncheolMemberId()),
        depositorNameOf(participation, refundAccount, c2c, paymentAmount),
        paymentAmount,
        shippingFees.shippingFeeOf(participation),
        participation.getStatus(),
        participation.getDueAt(),
        participation.getConfirmedAt(),
        refundAccountFor(participation, refundAccount, c2c, paymentAmount),
        delivery == null ? null : ManagementDeliveryResponse.from(delivery),
        participation.getPaymentSentAt(),
        participation.getBundleId() == null
            ? null
            : releasabilityByBundleId.get(participation.getBundleId()),
        // 묶음 확인 API 의 expectedSlotIds 는 이 값이 true 인 슬롯만 실어야 한다 — 판정을 서버가 준다.
        ParticipationStatus.payableStatuses().contains(participation.getStatus()));
  }

  /**
   * 통장 대조 키(입금자명). 값이 없으면 {@code null} 을 내리고 클라가 닉네임으로 폴백한다 ({@code
   * HostedBuncheolManage.tsx}).
   *
   * <p><b>null 이 되는 경우가 둘이고 이유가 다르다.</b>
   *
   * <ul>
   *   <li><b>계좌가 없는 행</b> — 참여 계좌 강제(PR #151) 이전의 0원 참여. 여기서 조건 없이 역참조해 개최 관리 화면 전체가
   *       500 이 났다. 계좌를 강제한 뒤에도 옛 행은 P4 컬럼 삭제까지 남으므로 가드를 유지한다. <b>유상 참여가 이 분기에
   *       들어오면 데이터 이상</b>이라 경고를 남긴다 — 닉네임 폴백 때문에 화면만 봐서는 티가 나지 않는다.
   *   <li><b>LEGACY 0원 참여</b> — 계좌를 갖게 된 뒤에도 <b>대조할 입금이 없어</b> 예금주(실명)를 내리지 않는다. 이 필드의
   *       존재 이유가 통장 대조이므로({@link BuncheolManagementParticipantResponse}), 대조할 것이 없으면 노출 근거도 없다.
   * </ul>
   *
   * <p>⚠️ <b>C2C 는 0원 슬롯이어도 내린다.</b> 0원 판정은 <b>슬롯</b> 단위인데 C2C 통장 대조는
   * <b>묶음</b> 단위다 — 배송비가 묶음당 1회만 붙고(추가 모집은 새 묶음이라 또 붙는다) 멤버 가격 0 도 허용돼서, 같은 사람의 "0원 슬롯 + 유상 슬롯"이 한 묶음이
   * 될 수 있다. 그 0원 행의 예금주를 지우면 이체 1건에 대조 키가 갈린다. C2C 가 0원 슬롯에도 계좌를 요구하는 이유가
   * 그것이다({@code ParticipationService#participateC2c}). LEGACY 는 1인 1참여라 0원 = 아무것도 안 낸 사람이다.
   *
   * <p>P2 에서 묶음이 정본이 되면 이 판정은 {@code bundle} 단위로 옮긴다 — 그때 플로우 분기가 사라진다.
   *
   * <p>⚠️ 판정은 <b>금액</b>으로 한다 — 계좌 유무로 바꾸면 안 된다. {@code DepositOrderListener}·{@code
   * SlackNotificationListener} 도 같은 기준이다.
   */
  private static String depositorNameOf(
      final Participation participation,
      final RefundAccount refundAccount,
      final boolean c2c,
      final long paymentAmount) {
    if (refundAccount == null) {
      if (paymentAmount > 0) {
        log.warn(
            "유상 참여의 묶음 계좌가 없다 — 개최자가 통장 대조 키를 잃는다. participationId={}, bundleId={}",
            participation.getId(),
            participation.getBundleId());
      }
      return null;
    }
    if (!c2c && paymentAmount == 0) {
      return null;
    }
    return refundAccount.holder();
  }

  /**
   * 개최자에게 내려줄 환불 계좌 (docs/70 결정 21). 통장 대조에 필요한 것은 입금자명뿐이라 평시에는 계좌를 내리지 않는다. 계좌번호가 필요한
   * 유일한 상황은 <b>개최자가 직접 환불해야 하는 건</b>이고, 그건 취소분 중 입금 흔적이 남은 건뿐이다.
   *
   * <p>판정 키({@code paymentSentAt} 또는 {@code confirmedAt})는 개최 관리 화면의 "환불이 필요한 참여" 목록 필터와 같은
   * 기준이다 — 둘이 갈리면 목록에는 뜨는데 계좌가 비는 행이 생긴다.
   */
  private static RefundAccountResponse refundAccountFor(
      final Participation participation,
      final RefundAccount refundAccount,
      final boolean c2c,
      final long paymentAmount) {
    return needsHostRefund(participation, c2c, paymentAmount)
        ? RefundAccountResponse.from(refundAccount)
        : null;
  }

  private static boolean needsHostRefund(
      final Participation participation, final boolean c2c, final long paymentAmount) {
    // LEGACY 0원(코드) 참여는 돌려줄 돈이 없다. 참여 계좌 강제(PR #151) 전에는 계좌가 NULL 이라 자동으로 걸러졌지만,
    // 이제는 계좌가 채워져 있어 이 조건이 없으면 취소 시 개최자에게 계좌번호까지 내려간다 — 0원 코드 참여는 생성 즉시
    // CONFIRMED 라 취소되면 아래 뒷 조건을 항상 만족한다.
    // C2C 를 제외하는 이유는 depositorNameOf 와 같다(슬롯 판정 ≠ 묶음 판정). 게다가 FE 의 "환불이 필요한 참여"
    // 목록 필터에는 금액 조건이 없어(HostedBuncheolManage.tsx — confirmedAt||paymentSentAt), C2C 0원 취소분을
    // 여기서 지우면 목록에는 뜨는데 계좌가 비는 행이 된다 — 아래 판정 키 규약이 경고하는 바로 그 상태다.
    if (!c2c && paymentAmount == 0) {
      return false;
    }
    return participation.getStatus() == ParticipationStatus.CANCELLED
        && (participation.getPaymentSentAt() != null || participation.getConfirmedAt() != null);
  }
}
