package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParticipationDomainService {

  private final ParticipationRepository participationRepository;

  public Participation createParticipation(final Participation participation) {
    return participationRepository.save(participation);
  }

  public boolean createInstantParticipationIfRecruiting(final Participation participation) {
    return participationRepository.saveInstantIfRecruiting(participation);
  }

  public Participation getParticipation(final Long id) {
    return participationRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));
  }

  public Optional<Participation> findCurrentBidParticipation(
      final Long buncheolMemberId, final Long participantId) {
    return participationRepository.findCurrentBidByBuncheolMemberIdAndParticipantId(
        buncheolMemberId, participantId);
  }

  public Optional<Participation> findActiveParticipation(
      final Long buncheolMemberId, final Long participantId) {
    return participationRepository.findActiveByBuncheolMemberIdAndParticipantId(
        buncheolMemberId, participantId);
  }

  public boolean isInstantSlotTaken(final Long buncheolMemberId) {
    return participationRepository.existsActiveInstantByBuncheolMemberId(buncheolMemberId);
  }

  public void failAllOpenBids(final Long buncheolMemberId, final String failReason) {
    participationRepository.failAllOpenBidsByBuncheolMemberId(buncheolMemberId, failReason);
  }

  public void updateParticipation(final Participation participation) {
    boolean updated = participationRepository.updateBid(participation);
    if (!updated) {
      throw new BusinessException(ErrorCode.PARTICIPATION_BID_UPDATE_NOT_ALLOWED);
    }
  }

  public void updateParticipationStatus(
      final Participation participation, final ParticipationStatus expectedStatus) {
    boolean updated = participationRepository.updateStatus(participation, expectedStatus);
    if (!updated) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }
}
