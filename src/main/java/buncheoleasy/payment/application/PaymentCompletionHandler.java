package buncheoleasy.payment.application;

public interface PaymentCompletionHandler {

  void validateOwnership(Long participationId, Long userId);

  void onPaymentCompleted(Long participationId);

  void onPaymentFailed(Long participationId, String failReason);
}
