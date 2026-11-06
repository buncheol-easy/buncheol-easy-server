package buncheoleasy.aidraft.infrastructure;

import buncheoleasy.aidraft.domain.AiDraftRequest;
import buncheoleasy.aidraft.domain.AiDraftRequestRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaAiDraftRequestRepositoryAdapter implements AiDraftRequestRepository {

  private final JpaAiDraftRequestRepository jpaAiDraftRequestRepository;

  @Override
  public AiDraftRequest save(final AiDraftRequest request) {
    return jpaAiDraftRequestRepository.save(request);
  }

  @Override
  public Optional<AiDraftRequest> findById(final Long id) {
    return jpaAiDraftRequestRepository.findById(id);
  }
}
