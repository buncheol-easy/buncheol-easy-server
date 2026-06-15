package buncheoleasy.delivery.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryService {

  private final DeliveryDomainService deliveryDomainService;
  private final ParticipationDomainService participationDomainService;
  private final BuncheolDomainService buncheolDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void registerTracking(
      final Long hostId, final Long deliveryId, final String trackingNumber) {
    Delivery delivery = deliveryDomainService.getDelivery(deliveryId);

    // 참여 → 분철 → 개최자 검증
    Participation participation =
        participationDomainService.getParticipation(delivery.getParticipationId());
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    buncheol.validateOwner(hostId);

    DeliveryStatus previousStatus = delivery.getStatus();
    delivery.registerTracking(trackingNumber, Instant.now(clock));
    deliveryDomainService.updateDeliveryStatus(delivery, previousStatus);
    eventPublisher.publishEvent(new TrackingRegisteredEvent(deliveryId));
  }

  public void confirmReceipt(final Long participantId, final Long deliveryId) {
    Delivery delivery = deliveryDomainService.getDelivery(deliveryId);

    // 참여자 본인 검증
    Participation participation =
        participationDomainService.getParticipation(delivery.getParticipationId());
    if (!participation.getParticipantId().equals(participantId)) {
      throw new BusinessException(ErrorCode.DELIVERY_NO_PERMISSION);
    }

    DeliveryStatus previousStatus = delivery.getStatus();
    delivery.confirmReceipt(Instant.now(clock));
    deliveryDomainService.updateDeliveryStatus(delivery, previousStatus);
  }
}
