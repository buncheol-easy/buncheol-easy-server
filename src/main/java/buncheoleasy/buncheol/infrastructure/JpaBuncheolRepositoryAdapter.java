package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBuncheolRepositoryAdapter implements BuncheolRepository {

  private static final Set<BuncheolStatus> ACTIVE_STATUSES =
      EnumSet.of(
          BuncheolStatus.RECRUITING,
          BuncheolStatus.CLOSED,
          BuncheolStatus.PAID,
          BuncheolStatus.SETTLING);

  private final JpaBuncheolRepository jpaBuncheolRepository;
  private final Clock clock;

  @Override
  public Buncheol save(Buncheol buncheol) {
    return jpaBuncheolRepository.save(buncheol);
  }

  @Override
  public Optional<Buncheol> findById(Long id) {
    return jpaBuncheolRepository.findById(id);
  }

  @Override
  public List<Buncheol> findAllByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jpaBuncheolRepository.findAllById(ids);
  }

  @Override
  public List<Buncheol> findAllByHostIdOrderByCreatedAtDesc(Long hostId) {
    return jpaBuncheolRepository.findAllByHostIdOrderByCreatedAtDesc(hostId);
  }

  @Override
  public boolean updateStatus(Buncheol buncheol, BuncheolStatus expectedStatus) {
    int updated =
        jpaBuncheolRepository.updateStatusIfMatches(
            buncheol.getId(), buncheol.getStatus(), Instant.now(clock), expectedStatus);
    return updated > 0;
  }

  @Override
  public boolean existsActiveByHostId(Long hostId) {
    return jpaBuncheolRepository.existsByHostIdAndStatusIn(hostId, ACTIVE_STATUSES);
  }
}
