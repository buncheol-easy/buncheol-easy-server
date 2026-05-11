package buncheoleasy.buncheol.domain;

import java.util.List;
import java.util.Optional;

public interface BuncheolRepository {

  Buncheol save(Buncheol buncheol);

  Optional<Buncheol> findById(Long id);

  List<Buncheol> findAllByIds(List<Long> ids);

  boolean updateStatus(Buncheol buncheol, BuncheolStatus expectedStatus);

  boolean existsActiveByHostId(Long hostId);
}
