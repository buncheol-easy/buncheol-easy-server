package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * {@link ParticipationBundleRepository} 어댑터.
 *
 * <p>참여({@code JpaParticipationRepositoryAdapter})와 달리 조건부 INSERT 가 필요 없다 — 슬롯 선점 경쟁은 여전히
 * {@code participations} 의 유니크가 담당하고, 묶음에는 유니크를 두지 않기로 했기 때문이다(docs/71 §8-3). 그래서 평범한 JPA
 * save 로 충분하다.
 */
@Repository
@RequiredArgsConstructor
public class JpaParticipationBundleRepositoryAdapter implements ParticipationBundleRepository {

  private final JpaParticipationBundleRepository jpaParticipationBundleRepository;

  @Override
  public ParticipationBundle save(final ParticipationBundle bundle) {
    return jpaParticipationBundleRepository.save(bundle);
  }

  @Override
  public Optional<ParticipationBundle> findById(final Long id) {
    return jpaParticipationBundleRepository.findById(id);
  }

  @Override
  public List<ParticipationBundle> findActiveByBuncheolIdAndParticipantId(
      final Long buncheolId, final Long participantId) {
    return jpaParticipationBundleRepository.findAllByBuncheolIdAndParticipantIdAndClosedAtIsNull(
        buncheolId, participantId);
  }

  @Override
  public List<ParticipationBundle> findAllByBuncheolId(final Long buncheolId) {
    return jpaParticipationBundleRepository.findAllByBuncheolIdOrderByIdAsc(buncheolId);
  }
}
