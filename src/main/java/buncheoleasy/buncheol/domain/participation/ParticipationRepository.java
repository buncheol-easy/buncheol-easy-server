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

  /**
   * 한 멤버 슬롯의 차순위 승계 후보를 1건 조회. ACTIVE_BID 이면서 closedRank 가 부여된(마감을 거친) 참여만 대상으로 하며 {@code
   * closedRank ASC, id ASC} 정렬의 첫 건(다음 순위)을 반환한다. 후보가 없으면 empty.
   */
  Optional<Participation> findTopActiveBidInSlot(Long buncheolMemberId);

  /**
   * 한 멤버 슬롯에 결제 진행 중(AWAITING_PAYMENT / PAYMENT_REPORTED / CONFIRMED) 참여가 존재하는지. 차순위 승계 전 중복 결제 대상이
   * 생기지 않도록 가드하는 용도다. 만료된 낙찰자를 FAILED 로 전이한 뒤 호출하면(영속성 컨텍스트 auto-flush) 해당 낙찰자는 집계에서 제외된다.
   */
  boolean existsPaymentInProgressInSlot(Long buncheolMemberId);

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
