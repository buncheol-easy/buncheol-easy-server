package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolStatus;
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
   * 공개 목록의 <b>마감(CONFIRMED) 그룹</b>을 {@code deadline DESC, id DESC}(현재와 가까운 마감순) 로 검색한다. 어댑터에서 {@link
   * BuncheolStatus#CONFIRMED} 를 전달한다.
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
  List<Buncheol> searchConfirmed(
      @Param("status") BuncheolStatus status,
      @Param("groupId") Long groupId,
      @Param("memberId") Long memberId,
      @Param("keyword") String keyword,
      @Param("cursorDeadline") Instant cursorDeadline,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  @Query(
      "SELECT COUNT(b) > 0 FROM Buncheol b "
          + "WHERE b.hostId = :hostId AND b.status IN :activeStatuses")
  boolean existsByHostIdAndStatusIn(
      @Param("hostId") Long hostId, @Param("activeStatuses") Set<BuncheolStatus> activeStatuses);

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

  // RECRUITING → CONFIRMED/CANCELLED CAS UPDATE (마감 판정·호스트 취소 공용). 선점한 단일 인스턴스만 1 을 회수해
  // 다중 인스턴스 중복 마감과 마감/취소 경합을 막는다.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :newStatus, b.finalizedAt = :now, b.updatedAt = :now "
          + "WHERE b.id = :buncheolId AND b.status = :recruitingStatus")
  int finalizeIfRecruiting(
      @Param("buncheolId") Long buncheolId,
      @Param("newStatus") BuncheolStatus newStatus,
      @Param("recruitingStatus") BuncheolStatus recruitingStatus,
      @Param("now") Instant now);
}
