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

  List<Buncheol> findAllByHostIdOrderByCreatedAtDesc(Long hostId);

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

  /** status 가 expectedStatus 인 경우에만 status 를 갱신한다 (compare-and-swap). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Buncheol b "
          + "SET b.status = :newStatus, "
          + "    b.updatedAt = :now "
          + "WHERE b.id = :id AND b.status = :expectedStatus")
  int updateStatusIfMatches(
      @Param("id") Long id,
      @Param("newStatus") BuncheolStatus newStatus,
      @Param("now") Instant now,
      @Param("expectedStatus") BuncheolStatus expectedStatus);

  @Query(
      "SELECT COUNT(b) > 0 FROM Buncheol b "
          + "WHERE b.hostId = :hostId AND b.status IN :activeStatuses")
  boolean existsByHostIdAndStatusIn(
      @Param("hostId") Long hostId, @Param("activeStatuses") Set<BuncheolStatus> activeStatuses);
}
