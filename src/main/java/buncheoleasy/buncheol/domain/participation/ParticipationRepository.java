package buncheoleasy.buncheol.domain.participation;

import java.time.Instant;
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

  /** 단일 분철의 ACTIVE_BID 참여만 {@code bidAmount DESC, id ASC} 정렬로 조회. 마감 시 멤버별 낙찰자 선정용. */
  List<Participation> findBiddingByBuncheolId(Long buncheolId);

  boolean updateStatus(Participation participation, ParticipationStatus expectedStatus);

  /**
   * 분철의 활성 참여({@link ParticipationStatus#activeUnderRecruiting()})를 모두 {@link
   * ParticipationStatus#CANCELLED} 로 일괄 전이한다. 호스트가 분철을 취소했을 때 자동 호출되어, 좀비 참여가 남지 않도록 보장한다.
   *
   * <p>구현체는 {@code @Modifying} bulk UPDATE 로 동작하므로 호출 측 트랜잭션({@code @Transactional}) 이 필수다.
   *
   * @return 갱신된 참여 행 수
   */
  int cancelActiveByBuncheolId(Long buncheolId, Instant now);
}
