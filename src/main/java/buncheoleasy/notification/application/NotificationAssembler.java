package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
   * 한 참여 요청의 슬롯 묶음을 알림 1건으로 합칠 때 쓴다. 분철·참여자·환불계좌·입금 기한은 묶음 내에서 동일하므로 첫 참여 기준으로 1회만 조회하고, 슬롯별로 다른
   * 멤버명만 묶어 그룹 멤버를 한 번에 해석한다.
   */
  public ParticipationBundleView loadByParticipations(final List<Long> participationIds) {
    List<Participation> participations =
        participationIds.stream().map(participationDomainService::getParticipation).toList();
    Participation first = participations.get(0);
    Buncheol buncheol = buncheolDomainService.getBuncheol(first.getBuncheolId());
    User participant = userDomainService.getUser(first.getParticipantId());
    long totalAmount = participations.stream().mapToLong(Participation::getTotalAmount).sum();
    return new ParticipationBundleView(
        buncheol,
        participant,
        resolveMemberNames(buncheol, participations),
        totalAmount,
        first.getDueAt(),
        first.getRefundAccount());
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

  // 그룹 멤버 조회는 이름 순서를 보장하지 않으므로 id 로 매핑해 슬롯(참여) 순서를 유지한다.
  private List<String> resolveMemberNames(
      final Buncheol buncheol, final List<Participation> participations) {
    List<Long> memberIds =
        participations.stream()
            .map(
                participation ->
                    buncheolMemberDomainService
                        .getBuncheolMember(participation.getBuncheolMemberId(), buncheol.getId())
                        .getMemberId())
            .toList();
    Map<Long, String> namesById =
        groupDomainService.getGroupMembersByIdsInGroup(buncheol.getGroupId(), memberIds).stream()
            .collect(Collectors.toMap(GroupMember::getId, GroupMember::getName));
    return memberIds.stream().map(namesById::get).toList();
  }
}
