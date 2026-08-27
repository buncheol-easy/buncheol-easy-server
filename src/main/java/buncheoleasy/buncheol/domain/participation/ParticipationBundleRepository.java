package buncheoleasy.buncheol.domain.participation;

import java.util.List;
import java.util.Optional;

/**
 * 참여 묶음 저장소 포트 (docs/70 §3).
 *
 * <p>P1 범위에서는 아직 어떤 서비스도 이 포트를 호출하지 않는다 — 백필과 P2 cutover 가 쓸 최소 조회만 둔다.
 */
public interface ParticipationBundleRepository {

  ParticipationBundle save(ParticipationBundle bundle);

  Optional<ParticipationBundle> findById(Long id);

  /**
   * 이 사람이 이 분철에서 지금 열고 있는 묶음 ({@code closed_at IS NULL}).
   *
   * <p><b>활성 묶음이 2개일 수 있다</b> — 추가 모집분이 새 묶음이기 때문이다(docs/71 §8-3). 그래서 반환이 단건이 아니라 목록이다.
   * DB 유니크로 1개를 강제하지 않기로 한 결정의 직접적 귀결이므로, 호출부는 "하나뿐" 을 전제하면 안 된다.
   */
  List<ParticipationBundle> findActiveByBuncheolIdAndParticipantId(
      Long buncheolId, Long participantId);

  /** 분철의 묶음 전체 (개최 관리·백필 검증용). */
  List<ParticipationBundle> findAllByBuncheolId(Long buncheolId);
}
