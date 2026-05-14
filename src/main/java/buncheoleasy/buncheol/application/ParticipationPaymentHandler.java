package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.payment.application.PaymentCompletionHandler;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
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
  public void onPaymentCompleted(final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    final ParticipationStatus previousStatus = participation.getStatus();

    Instant now = Instant.now(clock);
    participation.completePayment(now);

    participationDomainService.updateParticipationStatus(participation, previousStatus);

    createDeliverySnapshot(participation);
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
  public void onPaymentFailed(final Long participationId, final String failReason) {
    log.warn("낙찰자 결제 실패 - participationId: {}, reason: {}", participationId, failReason);
  }
}
