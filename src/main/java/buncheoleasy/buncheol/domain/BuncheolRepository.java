package buncheoleasy.buncheol.domain;

import java.util.Optional;

public interface BuncheolRepository {

  Buncheol save(Buncheol buncheol);

  Optional<Buncheol> findById(Long id);

  boolean updateStatus(Buncheol buncheol, BuncheolStatus expectedStatus);

  boolean existsActiveByHostId(Long hostId);
}
