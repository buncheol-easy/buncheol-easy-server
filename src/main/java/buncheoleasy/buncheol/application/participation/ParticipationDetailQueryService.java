package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.HostAccountResponse;
import buncheoleasy.buncheol.dto.response.ParticipationDetailResponse;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.UserDomainService;
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

    // 입금확인중일 때만 개최자 계좌를 노출한다 (입금 완료/취소 후에는 노출하지 않는다).
    HostAccountResponse hostAccount =
        participation.getStatus() == ParticipationStatus.AWAITING_PAYMENT
            ? HostAccountResponse.from(
                userDomainService.getUser(buncheol.getHostId()).getBankAccount())
            : null;

    return new ParticipationDetailResponse(
        participation.getId(),
        buncheol.getId(),
        buncheol.getTitle(),
        memberName,
        participation.getTotalAmount(),
        participation.getStatus(),
        participation.getCancelReason(),
        participation.getDueAt(),
        participation.getConfirmedAt(),
        hostAccount);
  }
}
