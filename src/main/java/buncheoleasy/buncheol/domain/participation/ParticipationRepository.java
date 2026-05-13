package buncheoleasy.buncheol.domain.participation;

import java.util.List;
import java.util.Optional;

public interface ParticipationRepository {

  boolean saveIfRecruiting(Participation participation);

  Optional<Participation> findById(Long id);

  List<Participation> findAllByParticipantIdOrderByCreatedAtDesc(Long participantId);

  Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      Long buncheolMemberId, Long participantId);

  boolean existsActiveByParticipantId(Long participantId);

  List<BuncheolActiveParticipationCount> countActiveByBuncheolIds(List<Long> buncheolIds);

  boolean updateStatus(Participation participation, ParticipationStatus expectedStatus);
}
