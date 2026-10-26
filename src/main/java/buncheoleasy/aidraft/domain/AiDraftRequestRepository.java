package buncheoleasy.aidraft.domain;

import java.util.Optional;

public interface AiDraftRequestRepository {

  AiDraftRequest save(AiDraftRequest request);

  Optional<AiDraftRequest> findById(Long id);
}
