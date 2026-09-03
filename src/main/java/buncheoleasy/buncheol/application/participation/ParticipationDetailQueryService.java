package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.application.payback.ShippingFeePaybackPolicy;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationCancellability;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.buncheol.dto.response.HostAccountResponse;
import buncheoleasy.buncheol.dto.response.ParticipationDetailResponse;
import buncheoleasy.buncheol.dto.response.ShippingFeePaybackResponse;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipationDetailQueryService {

  private final ParticipationDomainService participationDomainService;
  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final GroupMemberRepository groupMemberRepository;
  private final UserDomainService userDomainService;
  private final DeliveryRepository deliveryRepository;
  private final ShippingFeePaybackPolicy shippingFeePaybackPolicy;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final Clock clock;

  @Transactional(readOnly = true)
  public ParticipationDetailResponse getDetail(
      final Long participantId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);

    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    BuncheolMember member =
        buncheolMemberDomainService.getBuncheolMember(
            participation.getBuncheolMemberId(), buncheol.getId());
    String memberName =
        groupMemberRepository.findAllByIds(List.of(member.getMemberId())).stream()
            .findFirst()
            .map(GroupMember::getName)
            .orElse(null);

    // 입금 대기(입금확인중·보냈어요)일 때만 개최자 계좌를 노출한다 (입금 완료/취소 후에는 노출하지 않는다).
    // C2C 는 확정 시점 스냅샷 계좌를 쓴다 — 확정 후 개최자가 프로필 계좌를 바꿔도 안내가 어긋나지 않는다 (docs/46 §4.7-B1).
    boolean paymentPending =
        participation.getStatus() == ParticipationStatus.AWAITING_PAYMENT
            || participation.getStatus() == ParticipationStatus.PAYMENT_SENT;
    HostAccountResponse hostAccount =
        paymentPending
            ? HostAccountResponse.from(
                buncheol.isC2c()
                    ? buncheol.getPaymentAccount()
                    : userDomainService.getUser(buncheol.getHostId()).getBankAccount())
            : null;

    // 계좌·배송비의 정본은 묶음이다 (P2-c). 한 번만 꺼내 아래 두 곳에서 함께 쓴다.
    ParticipationBundle bundle =
        participationBundleDomainService.findByParticipation(participation).orElse(null);

    // 배송비 정본은 묶음이다 — 저장된 값을 그대로 더하면 배송비를 진 슬롯이 취소됐을 때 금액이 틀린다.
    // 🔴 환급 판정보다 <b>위</b>에 둔다. 대상 판정도 이 귀속을 봐야 「자격은 있는데 환급액은 0」이 안 생긴다.
    // 위에서 이미 읽은 묶음을 넘겨 형제 슬롯만 1회 더 읽는다(같은 묶음을 두 번 조회하던 것을 없앤다).
    ShippingFeeAttribution shippingFees =
        participationBundleDomainService.shippingFeeAttributionOf(bundle);

    // 택배 1개 = 묶음 1개 — 다슬롯 묶음은 슬롯들이 배송 1건을 공유한다.
    Delivery delivery =
        deliveryRepository.findByBundleId(participation.getBundleId()).orElse(null);
    ShippingFeePaybackResponse payback =
        ShippingFeePaybackResponse.of(
            participation,
            shippingFeePaybackPolicy.deriveStatus(
                participation, buncheol.getFlowType(), delivery, Instant.now(clock), shippingFees),
            shippingFeePaybackPolicy.submitDeadline(
                participation, buncheol.getFlowType(), delivery, shippingFees),
            bundle == null ? null : bundle.getRefundAccount());

    return new ParticipationDetailResponse(
        participation.getId(),
        participation.getBundleId(),
        buncheol.getId(),
        buncheol.getTitle(),
        memberName,
        shippingFees.totalAmountOf(participation),
        participation.getStatus(),
        participation.getCancelReason(),
        participation.getDueAt(),
        participation.getConfirmedAt(),
        hostAccount,
        payback,
        buncheol.getFlowType(),
        participation.getPaymentSentAt(),
        participation.getVisiblePaymentRejectedAt(),
        buncheol.getOpenChatUrl(),
        // 취소 API 게이트와 같은 판정을 그대로 내린다 — 화면이 상태로 재판정하면 서버와 갈린다 (docs/56 S-1).
        ParticipationCancellability.of(participation, buncheol));
  }
}
