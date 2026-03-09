package buncheoleasy.payment.application;

import buncheoleasy.payment.domain.PaymentPhase;

public interface PaymentCompletionHandler {

  void validateOwnership(Long participationId, Long userId);

  void onPaymentCompleted(Long participationId, PaymentPhase paymentPhase);

  void onPaymentFailed(Long participationId, PaymentPhase paymentPhase, String failReason);
}
