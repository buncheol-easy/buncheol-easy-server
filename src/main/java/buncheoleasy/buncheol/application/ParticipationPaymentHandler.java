package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentCompletionHandler;
import buncheoleasy.payment.domain.PaymentPhase;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationPaymentHandler implements PaymentCompletionHandler {

  private static final String FAIL_REASON_INSTANT_CONFIRMED = "즉시 구매 확정으로 인한 자동 실패 처리";

  private final ParticipationDomainService participationDomainService;
  private final DeliveryDomainService deliveryDomainService;
  private final ShippingAddressDomainService shippingAddressDomainService;
  private final UserDomainService userDomainService;
  private final Clock clock;

  @Override
  public void validateOwnership(final Long participationId, final Long userId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    if (!participation.getParticipantId().equals(userId)) {
      throw new BusinessException(ErrorCode.PAYMENT_NO_PERMISSION);
    }
  }

  @Override
  public void onPaymentCompleted(final Long participationId, final PaymentPhase paymentPhase) {
    Participation participation = participationDomainService.getParticipation(participationId);
    final ParticipationStatus previousStatus = participation.getStatus();

    LocalDateTime now = LocalDateTime.now(clock);
    switch (paymentPhase) {
      case PaymentPhase.INSTANT -> participation.confirmInstantParticipation(now);
      case PaymentPhase.DEPOSIT -> participation.activateBidAfterDepositPayment();
      case PaymentPhase.BALANCE -> participation.confirmBalancePayment(now);
      default -> throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }

    participationDomainService.updateParticipationStatus(participation, previousStatus);

    if (paymentPhase == PaymentPhase.INSTANT) {
      participationDomainService.failAllOpenBids(
          participation.getBuncheolMemberId(), FAIL_REASON_INSTANT_CONFIRMED);
    }

    // INSTANT 확정 참여의 배송 스냅샷은 분철 마감 시점에 일괄 생성 (TODO: 마감 로직 구현 시 처리)
    if (paymentPhase == PaymentPhase.BALANCE) {
      createDeliverySnapshot(participation);
    }
  }

  private void createDeliverySnapshot(final Participation participation) {
    ShippingAddress shippingAddress =
        shippingAddressDomainService.getShippingAddress(participation.getShippingAddressId());
    User user = userDomainService.getUser(participation.getParticipantId());

    Delivery delivery =
        Delivery.createSnapshot(
            participation.getId(),
            shippingAddress.getShippingMethod(),
            shippingAddress.getStoreName(),
            user.getNickname().value(),
            user.getPhoneNumber().value());
    deliveryDomainService.createDelivery(delivery);
  }

  @Override
  public void onPaymentFailed(
      final Long participationId, final PaymentPhase paymentPhase, final String failReason) {
    if (paymentPhase == PaymentPhase.BALANCE) {
      log.warn("잔금 결제 실패 - participationId: {}, reason: {}", participationId, failReason);
      return;
    }

    Participation participation = participationDomainService.getParticipation(participationId);
    final ParticipationStatus previousStatus = participation.getStatus();

    participation.fail(failReason, LocalDateTime.now(clock));

    participationDomainService.updateParticipationStatus(participation, previousStatus);
  }
}
