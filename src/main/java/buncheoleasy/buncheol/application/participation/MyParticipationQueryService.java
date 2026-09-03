package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.application.payback.ShippingFeePaybackPolicy;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationCancellability;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.buncheol.dto.response.HostAccountResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationDeliveryResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.buncheol.dto.response.ShippingFeePaybackResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import buncheoleasy.buncheol.dto.response.RequestedShippingAddressResponse;
import buncheoleasy.user.domain.shipping.ShippingAddressRepository;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final BuncheolImageRepository buncheolImageRepository;
  private final DeliveryRepository deliveryRepository;
  private final UserRepository userRepository;
  private final ShippingFeePaybackPolicy shippingFeePaybackPolicy;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final ShippingAddressRepository shippingAddressRepository;
  private final Clock clock;

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

    Map<Long, String> thumbnailByBuncheolId =
        buncheolImageRepository.findThumbnailsByBuncheolIds(buncheolIds).stream()
            .collect(Collectors.toMap(BuncheolImage::getBuncheolId, BuncheolImage::getImageUrl));

    // 택배 1개 = 묶음 1개. 다슬롯 묶음의 두 번째 슬롯에는 자기 배송 행이 없으므로(그 묶음의 배송 1건을
    // 슬롯들이 공유한다) 참여 id 로 찾으면 그 슬롯만 배송 정보가 비어 보인다.
    // 입금확인된 슬롯의 묶음만 조회한다 — 배송은 입금확인 시점에 생기므로 그 밖의 묶음은 애초에 배송이 없고,
    // 미확정 슬롯에 배송을 붙이지 않는 아래 게이트와 조회 범위를 맞춰 둔다.
    // 묶음이 없는 참여(배포선 창에서 생긴 행)는 걸러 낸다 — 그대로 넘기면 IS NULL 조회가 되어 남의 배송이 걸린다.
    List<Long> bundleIds =
        participations.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED)
            .map(Participation::getBundleId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, Delivery> deliveryByBundleId =
        deliveryRepository.findAllByBundleIds(bundleIds).stream()
            .collect(Collectors.toMap(Delivery::getBundleId, d -> d));

    Map<Long, HostAccountResponse> hostAccountByHostId =
        findHostAccountsForAwaitingPayments(participations, buncheolById);
    // 계좌·입금자명의 정본은 묶음이다 (P2-c). 건별로 읽으면 참여 수만큼 쿼리가 늘어난다(N+1).
    Map<Long, ParticipationBundle> bundleById =
        participationBundleDomainService.findAllByParticipations(participations);
    // 배송비도 정본이 묶음이다. 이 목록은 사용자의 참여 전체(상태 필터 없음)이고 묶음은 (분철·사람·사이클) 단위라
    // 한 묶음의 슬롯이 전부 이 안에 들어온다 — ofAllSlots 의 전제.
    ShippingFeeAttribution shippingFees =
        ShippingFeeAttribution.ofAllSlots(participations, bundleById);
    // 배송지도 정본이 묶음이다. 참여마다 읽으면 N+1 이라 id 를 모아 한 번에 조회한다.
    // ⚠️ 삭제 가드가 있어도 null 이 올 수 있다 — 옛 행은 묶음이 없고, 가드 이전에 지워진 주소도 있다.
    Map<Long, ShippingAddress> shippingAddressById =
        findShippingAddresses(participations, bundleById);

    final Instant now = Instant.now(clock);
    return participations.stream()
        .map(
            p ->
                toResponse(
                    p,
                    buncheolById,
                    buncheolMemberById,
                    slotCountByBuncheolId,
                    groupMemberNameById,
                    thumbnailByBuncheolId,
                    deliveryByBundleId,
                    hostAccountByHostId,
                    bundleById,
                    shippingFees,
                    shippingAddressById,
                    now))
        .toList();
  }

  /**
   * 이 참여가 속한 묶음의 배송지. 화면의 「배송지 고정 · 변경 불가」 자리에 그대로 들어간다.
   *
   * <p>{@code null} 이 될 수 있는 경우는 둘이다 — 묶음이 없는 옛 행이고 사본도 비었거나, 참조하던 배송지가
   * 지워졌거나. 둘 다 <b>정상 경로가 아니고</b>, 그렇다고 500 을 낼 일도 아니다(목록 전체가 안 뜬다). 화면이
   * "확인 중" 빈 상태로 그리게 null 을 내려보낸다 — <b>기본 배송지로 폴백하면 안 된다.</b> 그러면 「변경 불가」
   * 라벨이 붙은 자리에 실제로 가지 않을 주소가 확신에 차서 뜬다.
   */
  private static RequestedShippingAddressResponse requestedShippingAddressOf(
      final Participation participation,
      final Map<Long, ParticipationBundle> bundleById,
      final Map<Long, ShippingAddress> shippingAddressById) {
    Long shippingAddressId =
        ParticipationBundleDomainService.shippingAddressIdOf(bundleById, participation);
    if (shippingAddressId == null) {
      return null;
    }
    ShippingAddress shippingAddress = shippingAddressById.get(shippingAddressId);
    return shippingAddress == null ? null : RequestedShippingAddressResponse.from(shippingAddress);
  }

  /**
   * 참여들이 물고 있는 묶음 배송지를 한 번에 읽는다. 정본은 묶음이고, 묶음이 없는 옛 행만 사본으로 폴백한다
   * ({@code shippingAddressIdOf} 가 그 규약을 갖고 있다).
   *
   * <p>이미 읽어 둔 {@code bundleById} 에서 id 를 꺼내므로 <b>참여당 0 쿼리</b>가 보장된다 — 단건 메서드를
   * 쓰면 호출 순서에 기대게 되고, 순서가 바뀌는 순간 조용히 2N 이 된다.
   */
  private Map<Long, ShippingAddress> findShippingAddresses(
      final List<Participation> participations,
      final Map<Long, ParticipationBundle> bundleById) {
    List<Long> ids =
        participations.stream()
            .map(p -> ParticipationBundleDomainService.shippingAddressIdOf(bundleById, p))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return ids.isEmpty()
        ? Map.of()
        : shippingAddressRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(ShippingAddress::getId, a -> a));
  }

  // 참여 상세와 동일한 규칙: 입금확인중(AWAITING_PAYMENT) 참여에만 개최자 계좌를 노출한다.
  private Map<Long, HostAccountResponse> findHostAccountsForAwaitingPayments(
      final List<Participation> participations, final Map<Long, Buncheol> buncheolById) {
    List<Long> awaitingHostIds =
        participations.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.AWAITING_PAYMENT)
            .map(p -> buncheolById.get(p.getBuncheolId()))
            .filter(Objects::nonNull)
            .map(Buncheol::getHostId)
            .distinct()
            .toList();
    if (awaitingHostIds.isEmpty()) {
      return Map.of();
    }
    return userRepository.findAllByIds(awaitingHostIds).stream()
        .filter(user -> user.getBankAccount() != null)
        .collect(
            Collectors.toMap(User::getId, user -> HostAccountResponse.from(user.getBankAccount())));
  }

  private MyParticipationResponse toResponse(
      final Participation participation,
      final Map<Long, Buncheol> buncheolById,
      final Map<Long, BuncheolMember> buncheolMemberById,
      final Map<Long, Long> slotCountByBuncheolId,
      final Map<Long, String> groupMemberNameById,
      final Map<Long, String> thumbnailByBuncheolId,
      final Map<Long, Delivery> deliveryByBundleId,
      final Map<Long, HostAccountResponse> hostAccountByHostId,
      final Map<Long, ParticipationBundle> bundleById,
      final ShippingFeeAttribution shippingFees,
      final Map<Long, ShippingAddress> shippingAddressById,
      final Instant now) {
    Buncheol buncheol = buncheolById.get(participation.getBuncheolId());
    // 미연결 참여(배포선 창)는 묶음이 없다 — 계좌 없이 내려간다. 백필이 채우면 다음 조회부터 나온다.
    RefundAccount refundAccount =
        ParticipationBundleDomainService.refundAccountOf(bundleById, participation);
    BuncheolMember buncheolMember = buncheolMemberById.get(participation.getBuncheolMemberId());
    int slotCount =
        slotCountByBuncheolId.getOrDefault(participation.getBuncheolId(), 0L).intValue();
    // 🔴 <b>입금확인된 슬롯만</b> 배송을 문다. 배송은 이제 묶음에 붙어 있어(택배 1개 = 묶음 1개) 같은
    // 묶음의 미입금 슬롯도 키가 맞는데, 그대로 물리면 <b>입금하지도 않은 슬롯에 "배송중" 과 운송장</b>이
    // 뜬다.
    //
    // ⚠️ <b>이 가드를 지우지 마라.</b> 슬롯 단위 확정이 만들던 혼합 묶음은 막혔지만(#177), 그게 이 가드를
    // 불필요하게 만들지 않는다 — <b>취소된 슬롯</b>이 남는다. 확정되지 않은 슬롯은 언제나 존재할 수 있다.
    //
    // ⚠️ 맵을 조회하기 전에 null 도 걸러야 한다 — 취소분 렌더링은 Map.of() 를 넘기는데, 불변 맵은
    // null 키 조회에서 NPE 다. (ShippingFeeAttribution 과 같은 함정)
    Delivery delivery =
        participation.getStatus() == ParticipationStatus.CONFIRMED
                && participation.getBundleId() != null
            ? deliveryByBundleId.get(participation.getBundleId())
            : null;
    // 입금 대기(입금확인중·보냈어요)일 때만 계좌를 노출한다. C2C 는 확정 시점 스냅샷 계좌 (docs/46 §3-5·§4.7-B1).
    boolean paymentPending =
        participation.getStatus() == ParticipationStatus.AWAITING_PAYMENT
            || participation.getStatus() == ParticipationStatus.PAYMENT_SENT;
    HostAccountResponse hostAccount =
        !paymentPending
            ? null
            : buncheol.isC2c()
                ? HostAccountResponse.from(buncheol.getPaymentAccount())
                : hostAccountByHostId.get(buncheol.getHostId());
    // 입금자명 안내용. 참여 시점 예금주명이라 프로필의 현재 계좌와 다를 수 있고, 자동 입금확인은 이 값으로 매칭한다.
    String refundHolder =
        paymentPending && refundAccount != null ? refundAccount.holder() : null;
    return new MyParticipationResponse(
        participation.getId(),
        participation.getBundleId(),
        participation.getBuncheolId(),
        buncheol.getTitle(),
        slotCount,
        groupMemberNameById.get(buncheolMember.getMemberId()),
        shippingFees.totalAmountOf(participation),
        shippingFees.shippingFeeOf(participation),
        participation.getStatus(),
        participation.getCancelReason(),
        buncheol.getStatus(),
        buncheol.getDeadline(),
        participation.getDueAt(),
        participation.getConfirmedAt(),
        thumbnailByBuncheolId.get(participation.getBuncheolId()),
        ShippingOptionResponse.listFrom(buncheol.getShippingFeePolicy()),
        hostAccount,
        refundHolder,
        delivery == null ? null : MyParticipationDeliveryResponse.from(delivery),
        requestedShippingAddressOf(participation, bundleById, shippingAddressById),
        // 이미 배치 로딩된 배송 스냅샷으로 파생하므로 추가 쿼리가 없다.
        ShippingFeePaybackResponse.of(
            participation,
            shippingFeePaybackPolicy.deriveStatus(
                participation, buncheol.getFlowType(), delivery, now, shippingFees),
            shippingFeePaybackPolicy.submitDeadline(
                participation, buncheol.getFlowType(), delivery, shippingFees),
            refundAccount),
        buncheol.getFlowType(),
        participation.getPaymentSentAt(),
        participation.getVisiblePaymentRejectedAt(),
        buncheol.getOpenChatUrl(),
        // 취소 API 게이트와 같은 판정을 그대로 내린다 — 화면이 상태로 재판정하면 서버와 갈린다 (docs/56 S-1).
        ParticipationCancellability.of(participation, buncheol));
  }
}
