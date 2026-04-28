package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaBuncheolRepositoryAdapter implements BuncheolRepository {

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
}
