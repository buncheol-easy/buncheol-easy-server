package buncheoleasy.notification.application;

import buncheoleasy.buncheol.application.ParticipationPaymentAmountResolver;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
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
  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final GroupDomainService groupDomainService;
  private final UserDomainService userDomainService;
  private final DeliveryDomainService deliveryDomainService;
  private final ParticipationPaymentAmountResolver paymentAmountResolver;

  public ParticipationView loadByParticipation(final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    BuncheolMember buncheolMember =
        buncheolMemberDomainService.getBuncheolMember(
            participation.getBuncheolMemberId(), buncheol.getId());
    String memberName = resolveMemberName(buncheol.getGroupId(), buncheolMember.getMemberId());
    User participant = userDomainService.getUser(participation.getParticipantId());
    User host = userDomainService.getUser(buncheol.getHostId());
    long paymentAmount = paymentAmountResolver.resolve(participation);
    return new ParticipationView(
        participation, buncheol, memberName, participant, host, paymentAmount);
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
