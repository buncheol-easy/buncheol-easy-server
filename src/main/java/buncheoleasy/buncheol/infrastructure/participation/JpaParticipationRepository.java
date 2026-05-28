package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaParticipationRepository extends JpaRepository<Participation, Long> {

  /**
   * active_participant_id (DB STORED 컬럼) 로 활성 참여 조회.
   *
   * <p>active_participant_id 는 schema.sql 의 participations 테이블에 정의된 STORED generated column 으로
   * status 가 활성 상태일 때만 participant_id 값을 가지며 비활성 상태면 NULL. 코드 레벨에서 엔티티에 매핑하지 않고 이 native query 에서만
   * 사용한다.
   */
  @Query(
      value =
          "SELECT * FROM participations "
              + "WHERE buncheol_member_id = :buncheolMemberId "
              + "AND active_participant_id = :participantId LIMIT 1",
      nativeQuery = true)
  Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      @Param("buncheolMemberId") Long buncheolMemberId, @Param("participantId") Long participantId);

  List<Participation> findAllByParticipantIdOrderByCreatedAtDesc(Long participantId);

  @Query(
      "SELECT COUNT(p) > 0 FROM Participation p "
          + "WHERE p.participantId = :participantId AND p.status IN :activeStatuses")
  boolean existsActiveByParticipantId(
      @Param("participantId") Long participantId,
      @Param("activeStatuses") List<ParticipationStatus> activeStatuses);

  @Query(
      "SELECT new buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount("
          + "p.buncheolId, COUNT(p)) "
          + "FROM Participation p "
          + "WHERE p.buncheolId IN :buncheolIds AND p.status IN :activeStatuses "
          + "GROUP BY p.buncheolId")
  List<BuncheolActiveParticipationCount> countActiveByBuncheolIds(
      @Param("buncheolIds") List<Long> buncheolIds,
      @Param("activeStatuses") List<ParticipationStatus> activeStatuses);

  @Query(
      "SELECT p FROM Participation p "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :activeStatuses "
          + "ORDER BY p.bidAmount DESC, p.id ASC")
  List<Participation> findActiveByBuncheolId(
      @Param("buncheolId") Long buncheolId,
      @Param("activeStatuses") List<ParticipationStatus> activeStatuses);

  /** status 가 expectedStatus 인 경우에만 갱신 (compare-and-swap). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.dueAt = :dueAt, "
          + "    p.closedRank = :closedRank, "
          + "    p.failReason = :failReason, "
          + "    p.finalizedAt = :finalizedAt, "
          + "    p.status = :newStatus, "
          + "    p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :expectedStatus")
  int updateStatusIfMatches(
      @Param("id") Long id,
      @Param("dueAt") Instant dueAt,
      @Param("closedRank") Integer closedRank,
      @Param("failReason") String failReason,
      @Param("finalizedAt") Instant finalizedAt,
      @Param("newStatus") ParticipationStatus newStatus,
      @Param("now") Instant now,
      @Param("expectedStatus") ParticipationStatus expectedStatus);

  /**
   * 분철의 활성 참여를 모두 CANCELLED 로 일괄 전이. 호스트가 분철을 취소한 흐름에서 호출되어 좀비 참여가 남지 않도록 한다.
   *
   * <p>bulk UPDATE 는 {@link Participation#cancel(Instant)} 의 도메인 상태 가드를 우회한다. 호출자는 호스트가 분철을
   * 취소할 때만 진입한다는 invariant 를 책임지며, 향후 도메인 이벤트({@code ParticipationCancelled} 등)가 도입되면
   * {@code ApplicationEventPublisher} 로 cascade 발행하는 패턴으로 전환할 것.
   *
   * <p>또한 {@code @PreUpdate} 콜백이 발동되지 않으므로 {@code updatedAt} 을 직접 set 한다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :cancelledStatus, "
          + "    p.finalizedAt = :now, "
          + "    p.updatedAt = :now "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :activeStatuses")
  int cancelByBuncheolIdAndStatusIn(
      @Param("buncheolId") Long buncheolId,
      @Param("activeStatuses") Set<ParticipationStatus> activeStatuses,
      @Param("cancelledStatus") ParticipationStatus cancelledStatus,
      @Param("now") Instant now);
}
