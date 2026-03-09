package buncheoleasy.payment.dto.response;

import buncheoleasy.payment.application.PaymentOrderInfo;

public record CreatePaymentOrderResponse(
    String clientKey,
    String paymentOrderId,
    String paymentOrderName,
    long amount,
    String successUrl,
    String failUrl) {

  public static CreatePaymentOrderResponse from(final PaymentOrderInfo paymentOrderInfo) {
    return new CreatePaymentOrderResponse(
        paymentOrderInfo.clientKey(),
        paymentOrderInfo.paymentOrderId(),
        paymentOrderInfo.paymentOrderName(),
        paymentOrderInfo.amount(),
        paymentOrderInfo.successUrl(),
        paymentOrderInfo.failUrl());
  }
}
