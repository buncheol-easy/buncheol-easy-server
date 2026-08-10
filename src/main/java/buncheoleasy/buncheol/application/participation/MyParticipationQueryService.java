package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackPolicy;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
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

    List<Long> participationIds = participations.stream().map(Participation::getId).toList();
    Map<Long, Delivery> deliveryByParticipationId =
        deliveryRepository.findAllByParticipationIds(participationIds).stream()
            .collect(Collectors.toMap(Delivery::getParticipationId, d -> d));

    Map<Long, HostAccountResponse> hostAccountByHostId =
        findHostAccountsForAwaitingPayments(participations, buncheolById);

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
                    deliveryByParticipationId,
                    hostAccountByHostId,
                    now))
        .toList();
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
      final Map<Long, Delivery> deliveryByParticipationId,
      final Map<Long, HostAccountResponse> hostAccountByHostId,
      final Instant now) {
    Buncheol buncheol = buncheolById.get(participation.getBuncheolId());
    BuncheolMember buncheolMember = buncheolMemberById.get(participation.getBuncheolMemberId());
    int slotCount =
        slotCountByBuncheolId.getOrDefault(participation.getBuncheolId(), 0L).intValue();
    Delivery delivery = deliveryByParticipationId.get(participation.getId());
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
    String refundHolder = paymentPending ? participation.getRefundAccount().holder() : null;
    return new MyParticipationResponse(
        participation.getId(),
        participation.getBuncheolId(),
        buncheol.getTitle(),
        slotCount,
        groupMemberNameById.get(buncheolMember.getMemberId()),
        participation.getTotalAmount(),
        participation.getShippingFee(),
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
        // 이미 배치 로딩된 배송 스냅샷으로 파생하므로 추가 쿼리가 없다.
        ShippingFeePaybackResponse.of(
            participation,
            shippingFeePaybackPolicy.deriveStatus(participation, delivery, now),
            shippingFeePaybackPolicy.submitDeadline(participation, delivery)),
        buncheol.getFlowType(),
        participation.getPaymentSentAt(),
        buncheol.getOpenChatUrl());
  }
}
