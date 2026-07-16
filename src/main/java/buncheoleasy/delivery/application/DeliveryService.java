package buncheoleasy.delivery.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryService {

  private final DeliveryDomainService deliveryDomainService;
  private final ParticipationDomainService participationDomainService;
  private final BuncheolDomainService buncheolDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public void registerTracking(
      final Long hostId, final Long deliveryId, final String trackingNumber) {
    Delivery delivery = deliveryDomainService.getDelivery(deliveryId);

    // 참여 → 분철 → 개최자 검증
    Participation participation =
        participationDomainService.getParticipation(delivery.getParticipationId());
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    buncheol.validateOwner(hostId);

    // delivery status 는 호스트/운영자만 전이시킨다. 동시 등록이 겹쳐도 양쪽 모두 SHIPPING 으로
    // 수렴(번호만 last-write-wins)해 상태머신이 깨지지 않으므로 CAS 없이 더티체킹으로 커밋한다.
    // 잘못된 전이는 registerTracking 도메인 메서드가 막는다.
    // 단, DELIVERED 자동전이 스케줄러를 추가하면 이 "동일 방향 수렴" 가정이 깨지므로 CAS/@Version 재검토 필요.
    delivery.registerTracking(trackingNumber, Instant.now(clock));
    eventPublisher.publishEvent(new TrackingRegisteredEvent(deliveryId));
  }

  /** 관리자(운영자)의 운송장 등록. 개최자 소유권 검증 없이 모든 배송의 운송장을 등록할 수 있다는 점만 다르다. */
  @Transactional
  public void registerTrackingByAdmin(final Long deliveryId, final String trackingNumber) {
    Delivery delivery = deliveryDomainService.getDelivery(deliveryId);

    delivery.registerTracking(trackingNumber, Instant.now(clock));
    eventPublisher.publishEvent(new TrackingRegisteredEvent(deliveryId));
  }

  @Transactional
  public void confirmReceipt(final Long participantId, final Long deliveryId) {
    Delivery delivery = deliveryDomainService.getDelivery(deliveryId);

    // 참여자 본인 검증
    Participation participation =
        participationDomainService.getParticipation(delivery.getParticipationId());
    if (!participation.getParticipantId().equals(participantId)) {
      throw new BusinessException(ErrorCode.DELIVERY_NO_PERMISSION);
    }

    delivery.confirmReceipt(Instant.now(clock));
  }

  /** 관리자(운영자)의 수령완료 처리. 참여자 본인 검증 없이 모든 배송을 수령완료로 전이할 수 있다는 점만 다르다. */
  @Transactional
  public void confirmReceiptByAdmin(final Long deliveryId) {
    Delivery delivery = deliveryDomainService.getDelivery(deliveryId);

    delivery.confirmReceipt(Instant.now(clock));
  }
}
