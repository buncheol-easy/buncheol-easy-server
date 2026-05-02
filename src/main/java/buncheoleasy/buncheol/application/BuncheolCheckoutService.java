package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolCheckoutService {

  private final BuncheolParticipationService buncheolParticipationService;
  private final ParticipationDomainService participationDomainService;
  private final PaymentService paymentService;

  /** 분철 참여 신청. 결제는 마감 후 낙찰자에 한해 별도로 진행한다. */
  @Transactional
  public Participation participate(
      final Long buncheolId, final Long participantId, final ParticipateRequest request) {
    return buncheolParticipationService.createParticipation(buncheolId, participantId, request);
  }

  /** 마감 후 낙찰자의 결제 주문 생성. */
  public PaymentOrderInfo startPaymentCheckout(
      final Long participantId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);

    if (!participation.getParticipantId().equals(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }

    if (participation.getStatus() != ParticipationStatus.AWAITING_PAYMENT) {
      throw new BusinessException(ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED);
    }

    return paymentService.createPaymentOrder(
        participation.getId(), participation.getBidAmount(), "분철 낙찰자 결제");
  }
}
