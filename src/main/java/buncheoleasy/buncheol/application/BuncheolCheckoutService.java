package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentCompletionHandler;
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
  private final PaymentCompletionHandler paymentCompletionHandler;
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

  /**
   * 분철 낙찰자의 (mock) 결제 확정. 결제 수단이 확정되기 전까지는 이 API 호출 즉시 결제가 완료된 것으로 보고 참여를 CONFIRMED 로 전환한다(+배송
   * 스냅샷 생성). 추후 실제 PG 가 도입되면 이 진입점을 실제 결제 흐름으로 교체한다. AWAITING_PAYMENT(낙찰) 상태가 아니면 예외.
   */
  @Transactional
  public void confirmMockPayment(final Long participantId, final Long participationId) {
    paymentCompletionHandler.validateOwnership(participationId, participantId);
    paymentCompletionHandler.onPaymentCompleted(participationId);
  }

  /** 참여자 본인의 분철 참여 취소. 현재는 ACTIVE_BID 상태에서만 허용한다. */
  @Transactional
  public void cancelParticipation(final Long participantId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);

    participation.cancel(Instant.now(clock));
  }
}
