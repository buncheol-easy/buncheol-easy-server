package buncheoleasy.delivery.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
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

  /** 취소된 참여들의 배송 스냅샷을 일괄 삭제한다 (분철 취소 cascade 시 고아 스냅샷 정리). 호출 측 {@code @Transactional} 필수. */
  public void deleteByParticipationIds(final List<Long> participationIds) {
    deliveryRepository.deleteByParticipationIds(participationIds);
  }
}
