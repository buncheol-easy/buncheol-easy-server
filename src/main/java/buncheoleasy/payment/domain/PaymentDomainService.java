package buncheoleasy.payment.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentDomainService {

  private final PaymentRepository paymentRepository;

  public Payment create(final Payment payment) {
    return paymentRepository.save(payment);
  }

  public void updatePendingPayment(final Payment payment) {
    update(payment, PaymentStatus.PENDING);
  }

  public void updateConfirmingPayment(final Payment payment) {
    update(payment, PaymentStatus.CONFIRMING);
  }

  private void update(final Payment payment, final PaymentStatus expectedStatus) {
    boolean updated = paymentRepository.update(payment, expectedStatus);
    if (!updated) {
      throw new BusinessException(ErrorCode.PAYMENT_STATE_TRANSITION_INVALID);
    }
  }

  public Payment getPaymentByOrderId(final String orderId) {
    return paymentRepository
        .findByOrderId(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
  }

  public Optional<Payment> findLatestByParticipationId(final Long participationId) {
    return paymentRepository.findLatestByParticipationId(participationId);
  }
}
