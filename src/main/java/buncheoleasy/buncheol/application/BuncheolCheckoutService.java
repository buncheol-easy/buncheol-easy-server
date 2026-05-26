package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.application.PaymentService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolCheckoutService {

  private final BuncheolParticipationService buncheolParticipationService;
  private final ParticipationDomainService participationDomainService;
  private final PaymentService paymentService;
  private final Clock clock;

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
    participation.validateOwnedBy(participantId);

    if (participation.getStatus() != ParticipationStatus.AWAITING_PAYMENT) {
      throw new BusinessException(ErrorCode.PAYMENT_ORDER_CREATION_NOT_ALLOWED);
    }

    return paymentService.createPaymentOrder(
        participation.getId(), participation.getBidAmount(), "분철 낙찰자 결제");
  }

  /** 참여자 본인의 분철 참여 취소. 현재는 ACTIVE_BID 상태에서만 허용한다. */
  @Transactional
  public void cancelParticipation(final Long participantId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);

    // 도메인 가드 거부(BCH-067)는 try 밖에서 그대로 전파해 CAS 충돌(BCH-073) 과 구분한다.
    // cancel() 호출이 try 안으로 이동하면 두 경로의 ErrorCode 가 섞이므로 위치 유지.
    participation.cancel(Instant.now(clock));

    try {
      participationDomainService.updateParticipationStatus(
          participation, ParticipationStatus.ACTIVE_BID);
    } catch (BusinessException ex) {
      if (ex.getErrorCode() == ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID) {
        throw new BusinessException(ErrorCode.PARTICIPATION_CANCEL_CONFLICT);
      }
      throw ex;
    }
  }
}
