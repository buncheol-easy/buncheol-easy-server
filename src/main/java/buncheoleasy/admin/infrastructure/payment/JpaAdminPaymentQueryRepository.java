package buncheoleasy.admin.infrastructure.payment;

import buncheoleasy.admin.domain.payment.BuncheolConfirmedCount;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 결제 목록 전용 읽기 쿼리. 참여를 축으로 분철·그룹·참여자·멤버·배송을 엔티티 조인(ON)으로 한 번에 가져온다 — 개최 분철마다 관리 API 를 다시 부르는 기존
 * 프론트의 N+1 조회를 서버 단일 쿼리로 대체한다.
 */
interface JpaAdminPaymentQueryRepository extends JpaRepository<Participation, Long> {

  /**
   * 파생 상태 CASE 는 {@code AdminPaymentStatus} 의 이름 문자열과 일치해야 한다: 입금확인중 → AWAITING_CONFIRMATION,
   * 입금확인됨 → CONFIRMED, 취소됐지만 입금확인 이력이 있으면(분철 취소 cascade) → REFUND_REQUIRED, 그 외 취소 → CANCELLED.
   *
   * <p>참여자(User)는 soft delete 될 수 있고, 배송 스냅샷·그룹 멤버·배송지 원본(종료된 참여 한정 삭제 가능)은 없을 수 있어
   * LEFT JOIN 으로 행을 보존한다. 배송지(sa)는 입금확인 전에도 운영자가 "결제 요청 배송지" 를 확인할 수 있게 함께 내린다.
   */
  @Query(
      "SELECT p, b, g, u, gm, d, sa FROM Participation p "
          + "JOIN Buncheol b ON b.id = p.buncheolId "
          + "JOIN Group g ON g.id = b.groupId "
          + "LEFT JOIN User u ON u.id = p.participantId "
          + "LEFT JOIN BuncheolMember bm ON bm.id = p.buncheolMemberId "
          + "LEFT JOIN GroupMember gm ON gm.id = bm.memberId "
          + "LEFT JOIN Delivery d ON d.participationId = p.id "
          + "LEFT JOIN ShippingAddress sa ON sa.id = p.shippingAddressId "
          + "WHERE (:statusFilter IS NULL OR :statusFilter = "
          + "  CASE WHEN p.status = :appliedStatus THEN 'APPLIED' "
          + "       WHEN p.status = :awaitingStatus OR p.status = :paymentSentStatus "
          + "         THEN 'AWAITING_CONFIRMATION' "
          + "       WHEN p.status = :confirmedStatus THEN 'CONFIRMED' "
          + "       WHEN p.confirmedAt IS NOT NULL THEN 'REFUND_REQUIRED' "
          + "       ELSE 'CANCELLED' END) "
          + "  AND (:keyword IS NULL "
          + "       OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
          + "       OR LOWER(g.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
          + "       OR LOWER(gm.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
          + "       OR LOWER(CAST(u.nickname AS String)) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
          + "       OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
          + "  AND (:cursorCreatedAt IS NULL "
          + "       OR p.createdAt < :cursorCreatedAt "
          + "       OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)) "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<Object[]> findPaymentRows(
      @Param("statusFilter") String statusFilter,
      @Param("keyword") String keyword,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("appliedStatus") ParticipationStatus appliedStatus,
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("paymentSentStatus") ParticipationStatus paymentSentStatus,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      Pageable pageable);

  @Query(
      "SELECT new buncheoleasy.admin.domain.payment.BuncheolConfirmedCount(p.buncheolId, COUNT(p)) "
          + "FROM Participation p "
          + "WHERE p.buncheolId IN :buncheolIds AND p.status = :confirmedStatus "
          + "GROUP BY p.buncheolId")
  List<BuncheolConfirmedCount> countConfirmedByBuncheolIds(
      @Param("buncheolIds") List<Long> buncheolIds,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus);

  /**
   * 파생 상태별 건수 + 확인 대기 금액(멤버 금액+배송비) 합계. 빈 테이블이면 SUM 이 null 이라 COALESCE 로 0 처리한다. GROUP BY 없는
   * 집계라 항상 1행이며, 단일 {@code Object[]} 선언 시 Spring Data 가 이중 배열로 감싸므로 {@code List} 로 받는다.
   */
  @Query(
      "SELECT COALESCE(SUM(CASE WHEN p.status = :awaitingStatus OR p.status = :paymentSentStatus THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = :confirmedStatus THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = :cancelledStatus AND p.confirmedAt IS NOT NULL THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = :cancelledStatus AND p.confirmedAt IS NULL THEN 1 ELSE 0 END), 0), "
          + "COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = :awaitingStatus OR p.status = :paymentSentStatus THEN p.amount + p.shippingFee ELSE 0 END), 0) "
          + "FROM Participation p")
  List<Object[]> summarize(
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("paymentSentStatus") ParticipationStatus paymentSentStatus,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("cancelledStatus") ParticipationStatus cancelledStatus);
}
