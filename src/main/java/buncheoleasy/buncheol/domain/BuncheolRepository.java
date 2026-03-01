package buncheoleasy.buncheol.domain;

import java.util.Optional;

public interface BuncheolRepository {

    Buncheol save(Buncheol buncheol);

    Optional<Buncheol> findById(Long id);

    void update(Long id, BuncheolParams params);
}
