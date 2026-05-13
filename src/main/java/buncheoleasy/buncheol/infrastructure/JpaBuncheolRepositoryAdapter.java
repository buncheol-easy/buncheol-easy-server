package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.LocalDateTime;
import java.util.EnumSet;
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
          BuncheolStatus.GOODS_ORDERED,
          BuncheolStatus.SELLER_SHIPPING,
          BuncheolStatus.HOST_SHIPPING,
          BuncheolStatus.ALL_RECEIVED,
          BuncheolStatus.SETTLING,
          BuncheolStatus.SETTLED);

  private final JpaBuncheolRepository jpaBuncheolRepository;

  @Override
  public Buncheol save(Buncheol buncheol) {
    return jpaBuncheolRepository.save(buncheol);
  }

  @Override
  public Optional<Buncheol> findById(Long id) {
    return jpaBuncheolRepository.findById(id);
  }

  @Override
  public boolean updateStatus(Buncheol buncheol, BuncheolStatus expectedStatus) {
    int updated =
        jpaBuncheolRepository.updateStatusIfMatches(
            buncheol.getId(), buncheol.getStatus(), LocalDateTime.now(), expectedStatus);
    return updated > 0;
  }

  @Override
  public boolean existsActiveByHostId(Long hostId) {
    return jpaBuncheolRepository.existsByHostIdAndStatusIn(hostId, ACTIVE_STATUSES);
  }
}
