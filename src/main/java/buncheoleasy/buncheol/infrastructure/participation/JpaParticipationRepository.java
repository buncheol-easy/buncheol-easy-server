package buncheoleasy.buncheol.infrastructure.participation;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaParticipationRepository extends JpaRepository<Participation, Long> {

  /** active_participant_id (DB STORED 컬럼) 로 활성 참여 조회. */
  @Query(
      value =
          "SELECT * FROM participations "
              + "WHERE buncheol_member_id = :buncheolMemberId "
              + "AND active_participant_id = :participantId LIMIT 1",
      nativeQuery = true)
  Optional<Participation> findActiveByBuncheolMemberIdAndParticipantId(
      @Param("buncheolMemberId") Long buncheolMemberId, @Param("participantId") Long participantId);

  /** active_instant_member_id (DB STORED 컬럼) 로 활성 즉시구매 존재 여부 확인. */
  @Query(
      value =
          "SELECT COUNT(*) > 0 FROM participations "
              + "WHERE active_instant_member_id = :buncheolMemberId",
      nativeQuery = true)
  boolean existsActiveInstantByBuncheolMemberId(@Param("buncheolMemberId") Long buncheolMemberId);

  @Query(
      "SELECT COUNT(p) > 0 FROM Participation p "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :activeStatuses")
  boolean existsActiveByBuncheolId(
      @Param("buncheolId") Long buncheolId,
      @Param("activeStatuses") List<ParticipationStatus> activeStatuses);

  /**
   * (buncheol_member_id, has_active_instant, has_active_bid) 형태로 멤버별 참여 현황을 집계한다. native 결과를
   * Object[] 로 반환받아 어댑터에서 record 로 변환.
   */
  @Query(
      value =
          "SELECT buncheol_member_id AS bm, "
              + "MAX(CASE WHEN type = 'INSTANT' AND status IN ('PAYMENT_PENDING', 'CONFIRMED') THEN 1 ELSE 0 END) AS has_instant, "
              + "MAX(CASE WHEN type = 'BID' AND status IN ('PAYMENT_PENDING', 'ACTIVE_BID', 'AWAITING_BALANCE_PAYMENT', 'CONFIRMED') THEN 1 ELSE 0 END) AS has_bid "
              + "FROM participations "
              + "WHERE buncheol_id = :buncheolId "
              + "AND status IN ('PAYMENT_PENDING', 'ACTIVE_BID', 'AWAITING_BALANCE_PAYMENT', 'CONFIRMED') "
              + "GROUP BY buncheol_member_id",
      nativeQuery = true)
  List<Object[]> findActiveParticipationPresenceRows(@Param("buncheolId") Long buncheolId);

  @Query(
      "SELECT DISTINCT sa.shippingMethod "
          + "FROM Participation p, ShippingAddress sa "
          + "WHERE p.shippingAddressId = sa.id "
          + "AND p.buncheolId = :buncheolId "
          + "AND p.status IN :activeStatuses")
  List<buncheoleasy.user.domain.shipping.ShippingMethod> findActiveShippingMethodsByBuncheolId(
      @Param("buncheolId") Long buncheolId,
      @Param("activeStatuses") List<ParticipationStatus> activeStatuses);

  /** status 가 expectedStatus 인 경우에만 갱신 (compare-and-swap). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.balanceDueAmount = :balanceDueAmount, "
          + "    p.balanceDueAt = :balanceDueAt, "
          + "    p.closedRank = :closedRank, "
          + "    p.failReason = :failReason, "
          + "    p.finalizedAt = :finalizedAt, "
          + "    p.status = :newStatus, "
          + "    p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :expectedStatus")
  int updateStatusIfMatches(
      @Param("id") Long id,
      @Param("balanceDueAmount") Long balanceDueAmount,
      @Param("balanceDueAt") LocalDateTime balanceDueAt,
      @Param("closedRank") Integer closedRank,
      @Param("failReason") String failReason,
      @Param("finalizedAt") LocalDateTime finalizedAt,
      @Param("newStatus") ParticipationStatus newStatus,
      @Param("now") LocalDateTime now,
      @Param("expectedStatus") ParticipationStatus expectedStatus);

  /** 특정 멤버 슬롯의 진행 중 BID 참여를 한 번에 FAILED 처리. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Participation p "
          + "SET p.status = buncheoleasy.buncheol.domain.participation.ParticipationStatus.FAILED, "
          + "    p.failReason = :failReason, "
          + "    p.finalizedAt = :now, "
          + "    p.updatedAt = :now "
          + "WHERE p.buncheolMemberId = :buncheolMemberId "
          + "AND p.type = buncheoleasy.buncheol.domain.participation.ParticipationType.BID "
          + "AND p.status IN :openBidStatuses")
  int failAllOpenBidsByBuncheolMemberId(
      @Param("buncheolMemberId") Long buncheolMemberId,
      @Param("failReason") String failReason,
      @Param("now") LocalDateTime now,
      @Param("openBidStatuses") List<ParticipationStatus> openBidStatuses);
}
