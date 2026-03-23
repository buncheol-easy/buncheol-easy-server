package buncheoleasy.delivery.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryDomainService {

  private final DeliveryRepository deliveryRepository;

  public Delivery createDelivery(final Delivery delivery) {
    return deliveryRepository.save(delivery);
  }

  public Delivery getDelivery(final Long id) {
    return deliveryRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
  }

  public Delivery getDeliveryByParticipationId(final Long participationId) {
    return deliveryRepository
        .findByParticipationId(participationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
  }

  public void updateDeliveryStatus(final Delivery delivery, final DeliveryStatus expectedStatus) {
    boolean updated = deliveryRepository.updateStatus(delivery, expectedStatus);
    if (!updated) {
      throw new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }
  }
}
