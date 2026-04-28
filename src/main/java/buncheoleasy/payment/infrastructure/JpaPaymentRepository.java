package buncheoleasy.payment.infrastructure;

import buncheoleasy.payment.domain.Payment;
import buncheoleasy.payment.domain.PaymentPhase;
import buncheoleasy.payment.domain.PaymentStatus;
import buncheoleasy.payment.domain.PaymentTxType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaPaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByOrderId(String orderId);

  Optional<Payment> findTop1ByParticipationIdAndPaymentPhaseAndTxTypeOrderByCreatedAtDescIdDesc(
      Long participationId, PaymentPhase paymentPhase, PaymentTxType txType);

  /** status 가 expectedStatus 인 경우에만 수정 가능한 필드를 갱신한다 (compare-and-swap). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Payment p "
          + "SET p.paymentKey = :paymentKey, "
          + "    p.status = :newStatus, "
          + "    p.reason = :reason, "
          + "    p.approvedAt = :approvedAt, "
          + "    p.updatedAt = :now "
          + "WHERE p.id = :id AND p.status = :expectedStatus")
  int updateIfStatusMatches(
      @Param("id") Long id,
      @Param("paymentKey") String paymentKey,
      @Param("newStatus") PaymentStatus newStatus,
      @Param("reason") String reason,
      @Param("approvedAt") LocalDateTime approvedAt,
      @Param("now") LocalDateTime now,
      @Param("expectedStatus") PaymentStatus expectedStatus);
}
