package buncheoleasy.payment.application;

public record PaymentOrderInfo(
    String clientKey,
    String paymentOrderId,
    String paymentOrderName,
    long amount,
    String successUrl,
    String failUrl) {}
