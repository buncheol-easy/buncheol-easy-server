package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaParticipationRepository extends JpaRepository<Participation, Long> {

  List<Participation> findAllByParticipantIdOrderByCreatedAtDesc(Long participantId);

  boolean existsByParticipantIdAndStatusIn(
      Long participantId, Collection<ParticipationStatus> statuses);

  boolean existsByShippingAddressId(Long shippingAddressId);

  @Query(
      "SELECT new buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount("
          + "p.buncheolId, COUNT(p)) "
          + "FROM Participation p "
          + "WHERE p.buncheolId IN :buncheolIds AND p.status IN :activeStatuses "
          + "GROUP BY p.buncheolId")
  List<BuncheolActiveParticipationCount> countActiveByBuncheolIds(
      @Param("buncheolIds") List<Long> buncheolIds,
      @Param("activeStatuses") Collection<ParticipationStatus> activeStatuses);

  @Query(
      "SELECT p FROM Participation p "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :statuses "
          + "ORDER BY p.createdAt ASC, p.id ASC")
  List<Participation> findByBuncheolIdAndStatusIn(
      @Param("buncheolId") Long buncheolId,
      @Param("statuses") Collection<ParticipationStatus> statuses);

  @Query(
      "SELECT p.buncheolMemberId FROM Participation p "
          + "WHERE p.buncheolId IN :buncheolIds AND p.status IN :activeStatuses")
  List<Long> findActiveBuncheolMemberIds(
      @Param("buncheolIds") List<Long> buncheolIds,
      @Param("activeStatuses") Collection<ParticipationStatus> activeStatuses);

  List<Participation> findByBuncheolIdAndStatusOrderByCreatedAtAscIdAsc(
      Long buncheolId, ParticipationStatus status);

  List<Participation> findByBuncheolIdAndStatusAndCancelReason(
      Long buncheolId, ParticipationStatus status, ParticipationCancelReason cancelReason);

  int countByBuncheolIdAndStatus(Long buncheolId, ParticipationStatus status);

  List<Participation> findByStatusAndDueAtLessThanEqualOrderByDueAtAsc(
      ParticipationStatus status, Instant dueAt, Limit limit);

  /** AWAITING_PAYMENT 이고 입금 기한 내일 때만 CONFIRMED 로 전이 (호스트 수동 입금확인 CAS). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :confirmedStatus, p.confirmedAt = :now, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :awaitingStatus AND p.dueAt >= :now")
  int confirmPaymentIfAwaiting(
      @Param("id") Long id,
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("now") Instant now);

  /** AWAITING_PAYMENT 이고 입금 기한(dueAt)이 지났을 때만 지정 사유로 CANCELLED 로 전이 (입금 만료 CAS). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :cancelledStatus, p.cancelReason = :reason, "
          + "    p.cancelledAt = :now, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :awaitingStatus AND p.dueAt <= :now")
  int cancelIfAwaitingAndOverdue(
      @Param("id") Long id,
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("cancelledStatus") ParticipationStatus cancelledStatus,
      @Param("reason") ParticipationCancelReason reason,
      @Param("now") Instant now);

  /** 분철의 특정 상태 참여를 모두 지정 사유로 CANCELLED 로 일괄 전이 (분철 취소 cascade). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = :cancelledStatus, p.cancelReason = :reason, "
          + "    p.cancelledAt = :now, p.updatedAt = :now "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :targetStatuses")
  int cancelByBuncheolIdAndStatusIn(
      @Param("buncheolId") Long buncheolId,
      @Param("targetStatuses") Collection<ParticipationStatus> targetStatuses,
      @Param("cancelledStatus") ParticipationStatus cancelledStatus,
      @Param("reason") ParticipationCancelReason reason,
      @Param("now") Instant now);
}
