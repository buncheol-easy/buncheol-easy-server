package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.MemberParticipationPresence;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ParticipationMapper {

  int insertInstantIfRecruiting(Participation participation);

  int insertBidIfNoActiveInstant(Participation participation);

  Optional<Participation> findById(@Param("id") Long id);

  Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      @Param("buncheolMemberId") Long buncheolMemberId, @Param("participantId") Long participantId);

  boolean existsActiveInstantByBuncheolMemberId(@Param("buncheolMemberId") Long buncheolMemberId);

  boolean existsActiveByBuncheolId(@Param("buncheolId") Long buncheolId);

  List<MemberParticipationPresence> findActiveParticipationPresencesByBuncheolId(
      @Param("buncheolId") Long buncheolId);

  List<ShippingMethod> findActiveShippingMethodsByBuncheolId(@Param("buncheolId") Long buncheolId);

  int updateStatus(
      @Param("participation") Participation participation,
      @Param("expectedStatus") ParticipationStatus expectedStatus);

  void failAllOpenBidsByBuncheolMemberId(
      @Param("buncheolMemberId") Long buncheolMemberId, @Param("failReason") String failReason);
}
