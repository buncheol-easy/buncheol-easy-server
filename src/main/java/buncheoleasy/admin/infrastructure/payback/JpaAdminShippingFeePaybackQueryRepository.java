package buncheoleasy.admin.infrastructure.payback;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 배송비 환급 검수 목록 전용 읽기 쿼리. 신청 이력이 있는 참여(paybackStatus != NONE)를 축으로 분철·신청자·멤버를 엔티티 조인(ON)으로 한
 * 번에 가져온다 (결제 목록 {@code JpaAdminPaymentQueryRepository} 와 같은 패턴).
 */
interface JpaAdminShippingFeePaybackQueryRepository extends JpaRepository<Participation, Long> {

  // 신청자(User)는 soft delete 될 수 있고 그룹 멤버는 지워졌을 수 있어 LEFT JOIN 으로 행을 보존한다.
  @Query(
      "SELECT p, b, u, gm FROM Participation p "
          + "JOIN Buncheol b ON b.id = p.buncheolId "
          + "LEFT JOIN User u ON u.id = p.participantId "
          + "LEFT JOIN BuncheolMember bm ON bm.id = p.buncheolMemberId "
          + "LEFT JOIN GroupMember gm ON gm.id = bm.memberId "
          + "WHERE p.paybackStatus <> :noneStatus "
          + "  AND (:statusFilter IS NULL OR p.paybackStatus = :statusFilter) "
          + "  AND (:cursorRequestedAt IS NULL "
          + "       OR p.paybackRequestedAt < :cursorRequestedAt "
          + "       OR (p.paybackRequestedAt = :cursorRequestedAt AND p.id < :cursorId)) "
          + "ORDER BY p.paybackRequestedAt DESC, p.id DESC")
  List<Object[]> findPaybackRows(
      @Param("statusFilter") PaybackStatus statusFilter,
      @Param("noneStatus") PaybackStatus noneStatus,
      @Param("cursorRequestedAt") Instant cursorRequestedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);
}
