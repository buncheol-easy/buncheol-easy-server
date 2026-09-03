package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 알림 발송에 필요한 도메인 데이터를 조회해 조립한다. 멤버명은 분철 멤버 슬롯 → 그룹 멤버 2단계로 해석한다. */
@Component
@RequiredArgsConstructor
public class NotificationAssembler {

  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final GroupDomainService groupDomainService;
  private final UserDomainService userDomainService;
  private final DeliveryDomainService deliveryDomainService;

  public ParticipationView loadByParticipation(final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    BuncheolMember buncheolMember =
        buncheolMemberDomainService.getBuncheolMember(
            participation.getBuncheolMemberId(), buncheol.getId());
    String memberName = resolveMemberName(buncheol.getGroupId(), buncheolMember.getMemberId());
    User participant = userDomainService.getUser(participation.getParticipantId());
    User host = userDomainService.getUser(buncheol.getHostId());
    // 환불 계좌·입금자명의 정본은 묶음이다 (P2-c). 미연결 행이면 비어 있고, 그 경우 알림은 대체 문자열로 나간다.
    // ⚠️ 실제로 묶음을 쓰는 리스너는 3곳인데 loadByParticipation 을 타는 모든 알림이 조회를 1건 더 낸다 —
    // 응집도(조립은 여기 한 곳)와 맞바꾼 것이다. @Async 저부하 경로라 수용한다.
    ParticipationBundle bundle =
        participationBundleDomainService.findByParticipation(participation).orElse(null);
    // 🔴 입금 총액은 저장값이 아니라 <b>귀속 판정</b>으로 낸다. 저장값을 쓰면 배송비를 진 슬롯이 취소됐을 때
    // 알림톡이 화면보다 배송비만큼 적은 금액을 말한다 — 사용자는 "얼마 보내라"의 구속력이 큰 알림톡을 믿고
    // 그 금액을 보내므로, 화면만 고치면 결함이 남는 게 아니라 <b>두 숫자가 갈리는 상태</b>가 새로 생긴다.
    // 슬롯 단위로 판정해도 묶음 합계는 보존되므로, 다슬롯 합산 알림(sendFinalizedNotice)도 같이 맞는다.
    // 위에서 읽은 묶음을 그대로 넘긴다 — 다시 읽게 하면 알림 1건당 묶음 조회가 3건이 되고, 성사 확정
    // 알림은 수신자 수만큼 반복되므로 그 배수가 그대로 늘어난다.
    long paymentAmount =
        participationBundleDomainService
            .shippingFeeAttributionOf(bundle, participation.getId())
            .totalAmountOf(participation);
    return new ParticipationView(
        participation, bundle, buncheol, memberName, participant, host, paymentAmount);
  }

  public BuncheolHostView loadBuncheolHost(final Long buncheolId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    User host = userDomainService.getUser(buncheol.getHostId());
    return new BuncheolHostView(buncheol, host);
  }

  public Delivery loadDelivery(final Long deliveryId) {
    return deliveryDomainService.getDelivery(deliveryId);
  }

  private String resolveMemberName(final Long groupId, final Long memberId) {
    return groupDomainService
        .getGroupMembersByIdsInGroup(groupId, List.of(memberId))
        .get(0)
        .getName();
  }
}
