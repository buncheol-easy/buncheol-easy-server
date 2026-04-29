package buncheoleasy.payment.infrastructure;

import buncheoleasy.payment.domain.Payment;
import buncheoleasy.payment.domain.PaymentRepository;
import buncheoleasy.payment.domain.PaymentStatus;
import buncheoleasy.payment.domain.PaymentTxType;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaPaymentRepositoryAdapter implements PaymentRepository {

  private final JpaPaymentRepository jpaPaymentRepository;

  @Override
  public Payment save(final Payment payment) {
    return jpaPaymentRepository.save(payment);
  }

  @Override
  public boolean update(final Payment payment, final PaymentStatus expectedStatus) {
    int updated =
        jpaPaymentRepository.updateIfStatusMatches(
            payment.getId(),
            payment.getPaymentKey(),
            payment.getStatus(),
            payment.getReason(),
            payment.getApprovedAt(),
            LocalDateTime.now(),
            expectedStatus);
    return updated > 0;
  }

  @Override
  public Optional<Payment> findByOrderId(final String orderId) {
    return jpaPaymentRepository.findByOrderId(orderId);
  }

  @Override
  public Optional<Payment> findLatestByParticipationId(final Long participationId) {
    return jpaPaymentRepository.findTop1ByParticipationIdAndTxTypeOrderByCreatedAtDescIdDesc(
        participationId, PaymentTxType.PAYMENT);
  }
}
