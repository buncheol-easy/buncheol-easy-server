package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaParticipationBundleRepository extends JpaRepository<ParticipationBundle, Long> {

  List<ParticipationBundle> findAllByBuncheolIdAndParticipantIdAndClosedAtIsNull(
      Long buncheolId, Long participantId);

  List<ParticipationBundle> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);
}
