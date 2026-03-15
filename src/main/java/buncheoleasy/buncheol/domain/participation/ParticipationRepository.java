package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ParticipationRepository {

  boolean saveInstantIfRecruiting(Participation participation);

  boolean saveBidIfNoActiveInstant(Participation participation);

  Optional<Participation> findById(Long id);

  Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      Long buncheolMemberId, Long participantId);

  boolean existsActiveInstantByBuncheolMemberId(Long buncheolMemberId);

  boolean existsActiveByBuncheolId(Long buncheolId);

  List<MemberParticipationPresence> findActiveParticipationPresencesByBuncheolId(Long buncheolId);

  Set<ShippingMethod> findActiveShippingMethodsByBuncheolId(Long buncheolId);

  boolean updateStatus(Participation participation, ParticipationStatus expectedStatus);

  void failAllOpenBidsByBuncheolMemberId(Long buncheolMemberId, String failReason);
}
