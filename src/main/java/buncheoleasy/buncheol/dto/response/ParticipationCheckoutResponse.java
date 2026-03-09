package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.application.ParticipationCheckoutInfo;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;

public record ParticipationCheckoutResponse(
    Long participationId,
    ParticipationStatus participationStatus,
    String clientKey,
    String paymentOrderId,
    String paymentOrderName,
    long amount,
    String successUrl,
    String failUrl) {

  public static ParticipationCheckoutResponse from(final ParticipationCheckoutInfo checkoutInfo) {
    return new ParticipationCheckoutResponse(
        checkoutInfo.participation().getId(),
        checkoutInfo.participation().getStatus(),
        checkoutInfo.paymentOrder().clientKey(),
        checkoutInfo.paymentOrder().paymentOrderId(),
        checkoutInfo.paymentOrder().paymentOrderName(),
        checkoutInfo.paymentOrder().amount(),
        checkoutInfo.paymentOrder().successUrl(),
        checkoutInfo.paymentOrder().failUrl());
  }
}
