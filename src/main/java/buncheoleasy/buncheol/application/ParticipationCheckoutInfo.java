package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.payment.application.PaymentOrderInfo;

public record ParticipationCheckoutInfo(
    Participation participation, PaymentOrderInfo paymentOrder) {}
