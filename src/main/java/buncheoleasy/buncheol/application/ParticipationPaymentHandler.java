package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentCompletionHandler;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationPaymentHandler implements PaymentCompletionHandler {

  private final ParticipationDomainService participationDomainService;
  private final DeliverySnapshotCreator deliverySnapshotCreator;
  private final Clock clock;

  @Override
  public void validateOwnership(final Long participationId, final Long userId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    if (!participation.getParticipantId().equals(userId)) {
      throw new BusinessException(ErrorCode.PAYMENT_NO_PERMISSION);
    }
  }

  @Override
  public void onPaymentCompleted(final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    // AWAITING_PAYMENT → CONFIRMED. 도메인 가드(completePayment)가 상태를 검증하고, 변경은 dirty checking 으로 커밋 시 반영된다.
    // CAS updateStatus 와 혼용하면 flushAutomatically 가 dirty 변경을 먼저 flush 해 CAS WHERE 가 100% 실패하므로 함께 쓰지 않는다.
    participation.completePayment(Instant.now(clock));
    deliverySnapshotCreator.create(participation);
  }

  @Override
  public void onPaymentFailed(final Long participationId, final String failReason) {
    log.warn("낙찰자 결제 실패 - participationId: {}, reason: {}", participationId, failReason);
  }
}
