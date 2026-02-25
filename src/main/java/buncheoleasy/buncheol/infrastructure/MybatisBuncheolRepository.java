package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
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
}
