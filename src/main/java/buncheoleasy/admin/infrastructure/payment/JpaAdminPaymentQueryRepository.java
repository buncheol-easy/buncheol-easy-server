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
          + "LEFT JOIN Delivery d ON d.bundleId = p.bundleId "
          // 택배 1개 = 묶음 1개라 묶음으로 조인한다(다슬롯 묶음의 두 번째 슬롯도 같은 배송을 보게).
          // 🔴 단 <b>입금확인된 슬롯만</b> 문다. 같은 묶음의 미입금 슬롯도 키가 맞는데 그대로 물리면
          // 운영자가 <b>입금하지도 않은 슬롯을 배송 상태·운송장과 함께</b> 보게 되고, 이 화면은 입금 확인
          // 판단의 근거다. 혼재 묶음은 도달 가능하다 — 슬롯 단위 확인과 어드민 벌크 확인이 열려 있다.
          + "  AND p.status = :confirmedStatus "
          // ⚠️ 전환 이전 중복 행이 남아 있는 동안 묶음당 배송이 2건인 곳이 있어(prod 묶음 64) 그대로
          // 조인하면 그 참여가 목록에 두 줄로 나온다. id 최소값 1건으로 확정해 행 수를 보존한다.
          + "  AND d.id = (SELECT MIN(d2.id) FROM Delivery d2 WHERE d2.bundleId = p.bundleId) "
          // 🔴 배송지 정본도 묶음이다. 참여 사본으로 조인하면 신규 행에서 그 칸이 NULL 이라
          // 「요청 배송지」가 <b>예외 없이 조용히</b> 빈다 — 목록이라 발견이 늦다.
          // 미연결 옛 행만 사본으로 폴백한다(P4 에서 함께 사라진다).
          + "LEFT JOIN ParticipationBundle pb ON pb.id = p.bundleId "
          + "LEFT JOIN ShippingAddress sa "
          // ⚠️ COALESCE 를 쓰면 안 된다 — 묶음이 있는데 주소가 NULL 인 경우(참조 배송지가 삭제된 상태)에도
          // 사본으로 폴백해, ParticipationBundleDomainService#shippingAddressIdOf 가 명시적으로 금지한
          // 분기를 이 쿼리만 몰래 한다. 같은 PR 안에서 두 읽기가 다른 규칙을 따르면 다음 사람에게 함정이다.
          + "  ON sa.id = CASE WHEN p.bundleId IS NULL "
          + "                  THEN p.shippingAddressId ELSE pb.shippingAddressId END "
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
   * 확인 대기 슬롯을 가진 묶음들의 배송비 합계. 묶음당 1회만 더해지므로 어느 슬롯이 배송비를 지고 있든 결과가 같다 —
   * 목록 행의 귀속 판정과 합계가 일치하는 근거다.
   */
  @Query(
      "SELECT COALESCE(SUM(b.shippingFee), 0) FROM ParticipationBundle b "
          + "WHERE EXISTS (SELECT 1 FROM Participation p WHERE p.bundleId = b.id "
          + "  AND (p.status = :awaitingStatus OR p.status = :paymentSentStatus))")
  long sumPendingBundleShippingFee(
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("paymentSentStatus") ParticipationStatus paymentSentStatus);

  /**
   * 파생 상태별 건수 + 확인 대기 금액 합계. 빈 테이블이면 SUM 이 null 이라 COALESCE 로 0 처리한다. GROUP BY 없는
   * 집계라 항상 1행이며, 단일 {@code Object[]} 선언 시 Spring Data 가 이중 배열로 감싸므로 {@code List} 로 받는다.
   *
   * <p>🔴 <b>금액 합계에서 배송비는 여기서 더하지 않는다.</b> 배송비의 정본은 묶음이고 <b>묶음당 1회</b>라, 슬롯마다
   * {@code p.shippingFee} 를 더하면 배송비를 지던 슬롯이 취소된 묶음에서 그 1회분이 통째로 빠진다 — 같은 화면의
   * 목록 행(귀속 판정을 거친다)과 합계가 갈린다. 묶음 몫은 {@link #sumPendingBundleShippingFee} 가 따로 낸다.
   *
   * <p>⚠️ <b>미연결 참여(묶음 없는 행) 예외를 없앴다</b> — prod·staging 모두 0건 실측(2026-09-02). 다만 같은 화면의
   * <b>목록 행은</b> 그런 행에서 {@code ShippingFeeAttribution} 의 저장값 폴백을 타므로, 미연결 행이 다시 생기면
   * <b>합계(여기)와 목록이 갈린다.</b> 그때는 이 합계가 아니라 <b>미연결 행 자체</b>를 없애는 것이 답이다
   * (P4 의 {@code bundle_id NOT NULL} 승격).
   */
  @Query(
      "SELECT COALESCE(SUM(CASE WHEN p.status = :awaitingStatus OR p.status = :paymentSentStatus THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = :confirmedStatus THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = :cancelledStatus AND p.confirmedAt IS NOT NULL THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = :cancelledStatus AND p.confirmedAt IS NULL THEN 1 ELSE 0 END), 0), "
          + "COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = :awaitingStatus OR p.status = :paymentSentStatus "
          + "  THEN p.amount "
          + "  ELSE 0 END), 0) "
          + "FROM Participation p")
  List<Object[]> summarize(
      @Param("awaitingStatus") ParticipationStatus awaitingStatus,
      @Param("paymentSentStatus") ParticipationStatus paymentSentStatus,
      @Param("confirmedStatus") ParticipationStatus confirmedStatus,
      @Param("cancelledStatus") ParticipationStatus cancelledStatus);
}
