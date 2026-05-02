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

  @Query(
      "SELECT COUNT(p) > 0 FROM Participation p "
          + "WHERE p.buncheolId = :buncheolId AND p.status IN :activeStatuses")
  boolean existsActiveByBuncheolId(
      @Param("buncheolId") Long buncheolId,
      @Param("activeStatuses") List<ParticipationStatus> activeStatuses);

  /**
   * (buncheol_member_id, has_active_bid) 형태로 멤버별 활성 참여 존재 여부를 집계한다. native 결과를 Object[] 로 반환받아
   * 어댑터에서 record 로 변환.
   */
  @Query(
      value =
          "SELECT buncheol_member_id AS bm, "
              + "MAX(CASE WHEN status IN ('ACTIVE_BID', 'AWAITING_PAYMENT', 'CONFIRMED') THEN 1 ELSE 0 END) AS has_bid "
              + "FROM participations "
              + "WHERE buncheol_id = :buncheolId "
              + "AND status IN ('ACTIVE_BID', 'AWAITING_PAYMENT', 'CONFIRMED') "
              + "GROUP BY buncheol_member_id",
      nativeQuery = true)
  List<Object[]> findActiveParticipationPresenceRows(@Param("buncheolId") Long buncheolId);

  /**
   * 활성 참여들이 사용 중인 배송 방법 목록. 모듈 경계 보호를 위해 user 모듈의 ShippingAddress 엔티티를 직접 JPQL JOIN 하지 않고 native
   * query 로 테이블만 조인한다. 결과 String 은 어댑터에서 ShippingMethod enum 으로 변환.
   */
  @Query(
      value =
          "SELECT DISTINCT sa.shipping_method "
              + "FROM participations p "
              + "JOIN shipping_addresses sa ON p.shipping_address_id = sa.id "
              + "WHERE p.buncheol_id = :buncheolId "
              + "AND p.status IN (:activeStatuses)",
      nativeQuery = true)
  List<String> findActiveShippingMethodNamesByBuncheolId(
      @Param("buncheolId") Long buncheolId, @Param("activeStatuses") List<String> activeStatuses);

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
      @Param("dueAt") LocalDateTime dueAt,
      @Param("closedRank") Integer closedRank,
      @Param("failReason") String failReason,
      @Param("finalizedAt") LocalDateTime finalizedAt,
      @Param("newStatus") ParticipationStatus newStatus,
      @Param("now") LocalDateTime now,
      @Param("expectedStatus") ParticipationStatus expectedStatus);
}
