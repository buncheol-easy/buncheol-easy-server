package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisBuncheolRepository implements BuncheolRepository {

  private final BuncheolMapper buncheolMapper;

  @Override
  public Buncheol save(Buncheol buncheol) {
    buncheolMapper.insert(buncheol);
    return buncheol;
  }

  @Override
  public Optional<Buncheol> findById(Long id) {
    return buncheolMapper.findById(id);
  }

  @Override
  public void update(Long id, BuncheolParams params) {
    buncheolMapper.update(id, params);
  }

  @Override
  public void updateStatus(Long id, BuncheolStatus status) {
    buncheolMapper.updateStatus(id, status);
  }
}
