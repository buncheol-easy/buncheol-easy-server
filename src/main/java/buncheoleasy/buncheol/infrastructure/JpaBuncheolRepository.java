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
   * {@code excludedStatus} 와 일치하지 않는 분철을 검색한다. 어댑터에서 {@link BuncheolStatus#CANCELLED} 를 전달해 취소된 분철을
   * 목록에서 제외하는 용도로 사용한다.
   *
   * <p>각 필터는 인자가 {@code null} 이면 미적용된다. 정렬은 {@code createdAt DESC, id DESC}, 페이지 사이즈는 {@link
   * Pageable} 로 제어한다.
   */
  @Query(
      "SELECT b FROM Buncheol b "
          + "WHERE b.status <> :excludedStatus "
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
  List<Buncheol> search(
      @Param("excludedStatus") BuncheolStatus excludedStatus,
      @Param("groupId") Long groupId,
      @Param("memberId") Long memberId,
      @Param("keyword") String keyword,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
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
