package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
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
    // 입금 총액(멤버 금액 + 배송비)은 참여 생성 시 산정·스냅샷된 값을 그대로 쓴다.
    return new ParticipationView(
        participation, buncheol, memberName, participant, host, participation.getTotalAmount());
  }

  /**
   * C2C 성사 확정 시 입금 안내 대상(AWAITING_PAYMENT) 전체를 참여 단위 뷰로 조립한다 (docs/46 §4.1). 분철당 슬롯 수가 소규모라 참여별
   * 재조회(N+1)로 충분하다.
   */
  public List<ParticipationView> loadAwaitingViewsByBuncheol(final Long buncheolId) {
    return participationDomainService.findActiveByBuncheolId(buncheolId).stream()
        .filter(
            participation -> participation.getStatus() == ParticipationStatus.AWAITING_PAYMENT)
        .map(participation -> loadByParticipation(participation.getId()))
        .toList();
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
