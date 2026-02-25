package buncheoleasy.buncheol.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolDomainService {

    private final BuncheolRepository buncheolRepository;

    public Buncheol createBuncheol(final Long hostId, final BuncheolParams params) {
        return buncheolRepository.save(Buncheol.create(hostId, params));
    }
}
