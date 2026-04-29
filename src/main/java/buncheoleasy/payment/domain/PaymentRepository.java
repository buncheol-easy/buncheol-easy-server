package buncheoleasy.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

  Payment save(Payment payment);

  boolean update(Payment payment, PaymentStatus expectedStatus);

  Optional<Payment> findByOrderId(String orderId);

  Optional<Payment> findLatestByParticipationId(Long participationId);
}
