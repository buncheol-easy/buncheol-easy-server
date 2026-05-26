package buncheoleasy.buncheol.domain.participation;

import java.util.List;
import java.util.Optional;

public interface ParticipationRepository {

  boolean saveIfRecruiting(Participation participation);

  Optional<Participation> findById(Long id);

  List<Participation> findAllByParticipantIdOrderByCreatedAtDesc(Long participantId);

  Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      Long buncheolMemberId, Long participantId);

  boolean existsActiveByParticipantId(Long participantId);

  List<BuncheolActiveParticipationCount> countActiveByBuncheolIds(List<Long> buncheolIds);

  /** 단일 분철의 활성 참여 전체를 {@code bidAmount DESC, id ASC} 정렬로 조회. 멤버별 그룹핑·top N·순위 계산을 위한 단일 조회 진입점. */
  List<Participation> findActiveByBuncheolId(Long buncheolId);

  boolean updateStatus(Participation participation, ParticipationStatus expectedStatus);
}
