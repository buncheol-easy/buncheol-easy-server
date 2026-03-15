package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.MemberParticipationPresence;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisParticipationRepository implements ParticipationRepository {

  private final ParticipationMapper participationMapper;

  @Override
  public boolean saveInstantIfRecruiting(final Participation participation) {
    try {
      return participationMapper.insertInstantIfRecruiting(participation) > 0;
    } catch (DuplicateKeyException ex) {
      throw translateDuplicateKey(ex);
    }
  }

  @Override
  public boolean saveBidIfNoActiveInstant(final Participation participation) {
    try {
      return participationMapper.insertBidIfNoActiveInstant(participation) > 0;
    } catch (DuplicateKeyException ex) {
      throw translateDuplicateKey(ex);
    }
  }

  private BusinessException translateDuplicateKey(final DuplicateKeyException ex) {
    final String message = ex.getMessage();
    if (message != null && message.contains("uq_participations_active_member_participant")) {
      return new BusinessException(ErrorCode.PARTICIPATION_ALREADY_EXISTS);
    }
    return new BusinessException(ErrorCode.PARTICIPATION_MEMBER_ALREADY_TAKEN);
  }

  @Override
  public Optional<Participation> findById(final Long id) {
    return participationMapper.findById(id);
  }

  @Override
  public Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      final Long buncheolMemberId, final Long participantId) {
    return participationMapper.findActiveByBuncheolMemberIdAndParticipantId(
        buncheolMemberId, participantId);
  }

  @Override
  public boolean existsActiveInstantByBuncheolMemberId(final Long buncheolMemberId) {
    return participationMapper.existsActiveInstantByBuncheolMemberId(buncheolMemberId);
  }

  @Override
  public boolean existsActiveByBuncheolId(final Long buncheolId) {
    return participationMapper.existsActiveByBuncheolId(buncheolId);
  }

  @Override
  public List<MemberParticipationPresence> findActiveParticipationPresencesByBuncheolId(
      final Long buncheolId) {
    return participationMapper.findActiveParticipationPresencesByBuncheolId(buncheolId);
  }

  @Override
  public Set<ShippingMethod> findActiveShippingMethodsByBuncheolId(final Long buncheolId) {
    return new HashSet<>(participationMapper.findActiveShippingMethodsByBuncheolId(buncheolId));
  }

  @Override
  public boolean updateStatus(
      final Participation participation, final ParticipationStatus expectedStatus) {
    return participationMapper.updateStatus(participation, expectedStatus) > 0;
  }

  @Override
  public void failAllOpenBidsByBuncheolMemberId(
      final Long buncheolMemberId, final String failReason) {
    participationMapper.failAllOpenBidsByBuncheolMemberId(buncheolMemberId, failReason);
  }
}
