package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ParticipationRepository {

  boolean saveIfRecruiting(Participation participation);

  Optional<Participation> findById(Long id);

  Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      Long buncheolMemberId, Long participantId);

  boolean existsActiveByBuncheolId(Long buncheolId);

  boolean existsActiveByParticipantId(Long participantId);

  List<MemberParticipationPresence> findActiveParticipationPresencesByBuncheolId(Long buncheolId);

  Set<ShippingMethod> findActiveShippingMethodsByBuncheolId(Long buncheolId);

  boolean updateStatus(Participation participation, ParticipationStatus expectedStatus);
}
