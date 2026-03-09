package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationPolicy;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationType;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.application.PaymentService;
import buncheoleasy.payment.domain.PaymentPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolCheckoutService {

  private final BuncheolParticipationService buncheolParticipationService;
  private final ParticipationDomainService participationDomainService;
  private final PaymentService paymentService;

  @Transactional
  public ParticipationCheckoutInfo startCheckout(
      final Long buncheolId, final Long participantId, final ParticipateRequest request) {
    final Participation participation =
        buncheolParticipationService.createParticipation(buncheolId, participantId, request);

    final PaymentPhase paymentPhase;
    final long amount;
    final String paymentOrderName;

    if (participation.getType() == ParticipationType.INSTANT) {
      paymentPhase = PaymentPhase.INSTANT;
      amount = participation.getInstantPriceSnapshot();
      paymentOrderName = "분철 즉시 구매 결제";
    } else {
      paymentPhase = PaymentPhase.DEPOSIT;
      amount = ParticipationPolicy.resolveBidDepositAmount(participation.getBidAmount());
      paymentOrderName = "분철 제시 예치금 결제";
    }

    final PaymentOrderInfo paymentOrder =
        paymentService.createPaymentOrder(
            participation.getId(), paymentPhase, amount, paymentOrderName);
    return new ParticipationCheckoutInfo(participation, paymentOrder);
  }

  @Transactional
  public PaymentOrderInfo startBalancePaymentCheckout(
      final Long participantId, final Long participationId) {
    final Participation participation =
        participationDomainService.getParticipation(participationId);

    if (!participation.getParticipantId().equals(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }

    if (participation.getStatus() != ParticipationStatus.AWAITING_BALANCE_PAYMENT
        || participation.getBalanceDueAmount() == null
        || participation.getBalanceDueAmount() <= 0) {
      throw new BusinessException(ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED);
    }

    return paymentService.createPaymentOrder(
        participation.getId(),
        PaymentPhase.BALANCE,
        participation.getBalanceDueAmount(),
        "분철 잔금 결제");
  }
}
