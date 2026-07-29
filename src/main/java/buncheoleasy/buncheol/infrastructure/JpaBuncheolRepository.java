package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaBuncheolRepository extends JpaRepository<Buncheol, Long> {

  List<Buncheol> findAllByHostIdAndStatusNotOrderByCreatedAtDesc(
      Long hostId, BuncheolStatus excludedStatus);

  /**
   * 공개 목록의 <b>모집중(RECRUITING) 그룹</b>을 {@code createdAt DESC, id DESC}(최신 개최순) 로 검색한다. 어댑터에서 {@link
   * BuncheolStatus#RECRUITING} 을 전달한다.
   *
   * <p>각 필터는 인자가 {@code null} 이면 미적용된다. 커서가 있으면 {@code (createdAt, id)} 미만으로 keyset 페이지네이션한다. {@code
   * idx_buncheols_status_created (status, created_at DESC, id DESC)}(groupId 동반 시 {@code
   * idx_buncheols_group_created}) 인덱스로 정렬을 커버한다.
   */
  @Query(
      "SELECT b FROM Buncheol b "
          + "WHERE b.status = :status "
          + "  AND (:groupId IS NULL OR b.groupId = :groupId) "
          + "  AND (:memberId IS NULL OR b.id IN "
          + "        (SELECT bm.buncheolId FROM BuncheolMember bm WHERE bm.memberId = :memberId)) "
          + "  AND (:keyword IS NULL "
          + "        OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
          + "        OR LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
          + "  AND (:cursorCreatedAt IS NULL "
          + "        OR b.createdAt < :cursorCreatedAt "
          + "        OR (b.createdAt = :cursorCreatedAt AND b.id < :cursorId)) "
          + "ORDER BY b.createdAt DESC, b.id DESC")
  List<Buncheol> searchRecruiting(
      @Param("status") BuncheolStatus status,
      @Param("groupId") Long groupId,
      @Param("memberId") Long memberId,
      @Param("keyword") String keyword,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  /**
   * 공개 목록의 <b>deadline 기준 그룹</b>(진행확정 {@link BuncheolStatus#CONFIRMED} 또는 인원미달취소 {@link
   * BuncheolStatus#CANCELLED})을 {@code deadline DESC, id DESC}(현재와 가까운 마감순) 로 검색한다. 어댑터에서 그룹별 status 를
   * 전달해 두 그룹이 같은 쿼리를 공유한다.
   *
   * <p>각 필터는 인자가 {@code null} 이면 미적용된다. 커서가 있으면 {@code (deadline, id)} 미만으로 keyset 페이지네이션한다. {@code
   * idx_buncheols_status_deadline (status, deadline, id)} 인덱스를 역방향 스캔해 {@code deadline DESC, id DESC} 정렬을
   * 커버한다.
   */
  @Query(
      "SELECT b FROM Buncheol b "
          + "WHERE b.status = :status "
          + "  AND (:groupId IS NULL OR b.groupId = :groupId) "
          + "  AND (:memberId IS NULL OR b.id IN "
          + "        (SELECT bm.buncheolId FROM BuncheolMember bm WHERE bm.memberId = :memberId)) "
          + "  AND (:keyword IS NULL "
          + "        OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
          + "        OR LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
          + "  AND (:cursorDeadline IS NULL "
          + "        OR b.deadline < :cursorDeadline "
          + "        OR (b.deadline = :cursorDeadline AND b.id < :cursorId)) "
          + "ORDER BY b.deadline DESC, b.id DESC")
  List<Buncheol> searchByDeadline(
      @Param("status") BuncheolStatus status,
      @Param("groupId") Long groupId,
      @Param("memberId") Long memberId,
      @Param("keyword") String keyword,
      @Param("cursorDeadline") Instant cursorDeadline,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  /**
   * 호스트에게 아직 끝나지 않은 분철이 있는지 (회원탈퇴 가드). 모집중이거나, 진행확정됐지만 호스트가 입금확인해 줘야 할 참여(입금 확인 중)가
   * 남아 있거나, 입금확인 참여 중 배송이 끝나지 않은 건이 하나라도 남아 있으면 끝나지 않은 분철로 판정한다 (배송 스냅샷이 없는 입금확인 참여도
   * 미종료로 본다). 진행확정 분철의 입금 확인 중 참여는 만료 스케줄러가 곧 취소하는 게 정상 흐름이지만, 스케줄러 지연 사이에 호스트가 탈퇴하면
   * 해당 참여자의 참여 상세(호스트 조회)가 깨지므로 배송 조건 없이 미종료로 본다.
   */
  @Query(
      "SELECT COUNT(b) > 0 FROM Buncheol b "
          + "WHERE b.hostId = :hostId "
          + "AND (b.status = :recruitingStatus "
          + "  OR (b.status = :confirmedStatus "
          + "    AND EXISTS ("
          + "      SELECT p FROM Participation p "
          + "      WHERE p.buncheolId = b.id "
          + "      AND (p.status = :awaitingParticipationStatus "
          + "        OR (p.status = :confirmedParticipationStatus "
          + "          AND NOT EXISTS ("
          + "            SELECT d FROM Delivery d "
          + "            WHERE d.participationId = p.id "
          + "            AND d.status IN :finishedDeliveryStatuses))))))")
  boolean existsUnfinishedByHostId(
      @Param("hostId") Long hostId,
      @Param("recruitingStatus") BuncheolStatus recruitingStatus,
      @Param("confirmedStatus") BuncheolStatus confirmedStatus,
      @Param("awaitingParticipationStatus") ParticipationStatus awaitingParticipationStatus,
      @Param("confirmedParticipationStatus") ParticipationStatus confirmedParticipationStatus,
      @Param("finishedDeliveryStatuses") Set<DeliveryStatus> finishedDeliveryStatuses);

  // 최근 N일 분철 등록 수 상위 그룹 id 만 인기도 순으로 반환. Group 본문 매핑은 호출 측 책임.
  // 부정 조건(`status <> CANCELLED`) 대신 IN 절을 써 옵티마이저가 인덱스 활용을 안정적으로 판단하게 한다.
  @Query(
      "SELECT b.groupId FROM Buncheol b "
          + "WHERE b.status IN :statuses AND b.createdAt >= :since "
          + "GROUP BY b.groupId "
          + "ORDER BY COUNT(b) DESC, b.groupId DESC")
  List<Long> findGroupIdsByBuncheolCountSince(
      @Param("since") Instant since,
      @Param("statuses") Set<BuncheolStatus> statuses,
      Pageable pageable);

  // deadline 이 지난 특정 상태(RECRUITING) 분철 id 만 deadline 오름차순으로 조회. 자동 마감 폴링용. limit 은 Pageable 로 제어.
  @Query(
      "SELECT b.id FROM Buncheol b "
          + "WHERE b.status = :status AND b.deadline <= :now "
          + "ORDER BY b.deadline ASC")
  List<Long> findIdsByStatusAndDeadlineBefore(
      @Param("status") BuncheolStatus status, @Param("now") Instant now, Pageable pageable);

  // expectedStatus → newStatus CAS UPDATE (호스트 취소 공용: RECRUITING/CANCELLED → HOST_CANCELLED).
  // 선점한 단일 인스턴스만 1 을 회수해 다중 인스턴스 중복 마감과 마감/취소 경합을 막는다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :newStatus, b.finalizedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :expectedStatus")
  int finalizeIfStatus(
      @Param("buncheolId") Long buncheolId,
      @Param("expectedStatus") BuncheolStatus expectedStatus,
      @Param("newStatus") BuncheolStatus newStatus,
      @Param("now") Instant now);

  // 마감 판정 전용 CAS: 입금확인된(CONFIRMED) 참여 수를 서브쿼리로 세어 minHeadcount 충족 여부에 따라 CONFIRMED/CANCELLED 로
  // 한 UPDATE 안에서 전이한다. count 를 별도 SELECT 로 먼저 읽으면(REPEATABLE READ 스냅샷) 그 사이 입금확인이 커밋돼 stale count 로
  // 오판(충족인데 CANCELLED)할 수 있어, 카운트·비교·전이를 한 UPDATE 로 묶는다.
  // 단, 이 원자성은 InnoDB 가 UPDATE 평가 시 서브쿼리를 current read(최신 커밋) 로 보는 동작에 의존한다(SQL 표준 보장 아님).
  // H2 통합 테스트는 단일 스레드 기능(CONFIRMED/CANCELLED/이미마감 분기)만 검증하며 동시성 레이스 자체를 재현하지는 않는다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = CASE WHEN ("
          + "      SELECT COUNT(p) FROM Participation p "
          + "      WHERE p.buncheolId = b.id AND p.status = :confirmedParticipationStatus"
          + "    ) >= b.minHeadcount THEN :confirmedStatus ELSE :cancelledStatus END, "
          + "  b.finalizedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :recruitingStatus")
  int finalizeExpiredByConfirmedHeadcount(
      @Param("buncheolId") Long buncheolId,
      @Param("recruitingStatus") BuncheolStatus recruitingStatus,
      @Param("confirmedParticipationStatus") ParticipationStatus confirmedParticipationStatus,
      @Param("confirmedStatus") BuncheolStatus confirmedStatus,
      @Param("cancelledStatus") BuncheolStatus cancelledStatus,
      @Param("now") Instant now);

  // 조기 진행확정 전용 CAS: 입금확인(CONFIRMED) 참여 수가 전체 슬롯 수 이상(매진+전원확정)이고 minHeadcount 도 충족할 때만
  // RECRUITING → CONFIRMED. count 를 UPDATE WHERE 서브쿼리로 current-read 로 세므로, 마지막 슬롯들을 동시에 입금확인하는
  // 경합에서도 non-locking COUNT 를 미리 읽고 판정하는 read-then-act 갭 없이 즉시 확정한다(이 CAS 가 확정하면 이후 deadline 스케줄러는
  // status 불일치로 이중 확정하지 않는다).
  // totalSlots=0 이면 minHeadcount(≥1) <= 0 이 거짓이라 전이하지 않는다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :confirmedStatus, b.finalizedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :recruitingStatus "
          + "AND b.minHeadcount <= :totalSlots "
          + "AND ("
          + "  SELECT COUNT(p) FROM Participation p "
          + "  WHERE p.buncheolId = b.id AND p.status = :confirmedParticipationStatus"
          + ") >= :totalSlots")
  int confirmIfAllSlotsConfirmed(
      @Param("buncheolId") Long buncheolId,
      @Param("totalSlots") long totalSlots,
      @Param("recruitingStatus") BuncheolStatus recruitingStatus,
      @Param("confirmedParticipationStatus") ParticipationStatus confirmedParticipationStatus,
      @Param("confirmedStatus") BuncheolStatus confirmedStatus,
      @Param("now") Instant now);
}
