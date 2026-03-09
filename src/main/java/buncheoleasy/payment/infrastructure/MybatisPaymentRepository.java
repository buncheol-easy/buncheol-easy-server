package buncheoleasy.payment.infrastructure;

import buncheoleasy.payment.domain.Payment;
import buncheoleasy.payment.domain.PaymentPhase;
import buncheoleasy.payment.domain.PaymentRepository;
import buncheoleasy.payment.domain.PaymentStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisPaymentRepository implements PaymentRepository {

  private final PaymentMapper paymentMapper;

  @Override
  public Payment save(final Payment payment) {
    paymentMapper.insert(payment);
    return payment;
  }

  @Override
  public boolean update(final Payment payment, final PaymentStatus expectedStatus) {
    return paymentMapper.update(payment, expectedStatus) > 0;
  }

  @Override
  public Optional<Payment> findByOrderId(final String orderId) {
    return paymentMapper.findByOrderId(orderId);
  }

  @Override
  public Optional<Payment> findLatestPaymentByParticipationIdAndPaymentPhase(
      final Long participationId, final PaymentPhase paymentPhase) {
    return paymentMapper.findLatestPaymentByParticipationIdAndPaymentPhase(
        participationId, paymentPhase);
  }
}
