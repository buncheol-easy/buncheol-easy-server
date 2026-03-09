package buncheoleasy.buncheol.domain.participation;

import java.util.Optional;

public interface ParticipationRepository {

  Participation save(Participation participation);

  boolean saveInstantIfRecruiting(Participation participation);

  Optional<Participation> findById(Long id);

  Optional<Participation> findCurrentBidByBuncheolMemberIdAndParticipantId(
      Long buncheolMemberId, Long participantId);

  Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      Long buncheolMemberId, Long participantId);

  boolean existsActiveInstantByBuncheolMemberId(Long buncheolMemberId);

  boolean updateBid(Participation participation);

  boolean updateStatus(Participation participation, ParticipationStatus expectedStatus);

  void failAllOpenBidsByBuncheolMemberId(Long buncheolMemberId, String failReason);
}
