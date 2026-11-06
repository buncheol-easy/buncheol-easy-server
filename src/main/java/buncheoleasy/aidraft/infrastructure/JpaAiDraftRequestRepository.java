package buncheoleasy.aidraft.infrastructure;

import buncheoleasy.aidraft.domain.AiDraftRequest;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAiDraftRequestRepository extends JpaRepository<AiDraftRequest, Long> {}
