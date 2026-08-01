package buncheoleasy.delivery.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
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
    Buncheol buncheol = getBuncheolOf(delivery);
    buncheol.validateOwner(hostId);
    validateBuncheolConfirmed(buncheol);

    // 웹훅 자동 전이(DELIVERED/RECEIVED)와 겹칠 수 있으므로 전이는 CAS 로만 한다. 위에서 조회한
    // 엔티티는 검증용 데이터 홀더일 뿐 in-memory 로 바꾸지 않는다 (더티체킹 + CAS 혼용 금지).
    deliveryDomainService.registerTracking(deliveryId, trackingNumber, Instant.now(clock));
    eventPublisher.publishEvent(new TrackingRegisteredEvent(deliveryId));
  }

  /** 관리자(운영자)의 운송장 등록. 개최자 소유권 검증 없이 모든 배송의 운송장을 등록할 수 있다는 점만 다르다. */
  @Transactional
  public void registerTrackingByAdmin(final Long deliveryId, final String trackingNumber) {
    Delivery delivery = deliveryDomainService.getDelivery(deliveryId);

    validateBuncheolConfirmed(getBuncheolOf(delivery));

    deliveryDomainService.registerTracking(deliveryId, trackingNumber, Instant.now(clock));
    eventPublisher.publishEvent(new TrackingRegisteredEvent(deliveryId));
  }

  private Buncheol getBuncheolOf(final Delivery delivery) {
    Participation participation =
        participationDomainService.getParticipation(delivery.getParticipationId());
    return buncheolDomainService.getBuncheol(participation.getBuncheolId());
  }

  // 운송장 등록(발송 시작)은 분철 진행확정(CONFIRMED) 후에만 허용한다. 모집중 발송을 허용하면 마감 시점 최소 인원
  // 미달로 분철이 취소될 때 이미 발송된 물건과 환불·배송 스냅샷 정리(취소 cascade)가 모순되기 때문이다. 관리자 경로도
  // 동일하게 막아 "모집중엔 배송중 참여가 없다"는 불변식을 보장한다. CONFIRMED 는 이후 RECRUITING 으로 되돌아가지
  // 않으므로 check-then-act 갭이 없다.
  private void validateBuncheolConfirmed(final Buncheol buncheol) {
    if (buncheol.getStatus() != BuncheolStatus.CONFIRMED) {
      throw new BusinessException(ErrorCode.DELIVERY_BUNCHEOL_NOT_CONFIRMED);
    }
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

    deliveryDomainService.confirmReceipt(deliveryId, Instant.now(clock));
  }

  /** 관리자(운영자)의 수령완료 처리. 참여자 본인 검증 없이 모든 배송을 수령완료로 전이할 수 있다는 점만 다르다. */
  @Transactional
  public void confirmReceiptByAdmin(final Long deliveryId) {
    // 존재 검증 선행 — CAS 실패(상태 위반)와 미존재(NOT_FOUND)를 구분해 응답한다.
    deliveryDomainService.getDelivery(deliveryId);

    deliveryDomainService.confirmReceipt(deliveryId, Instant.now(clock));
  }
}
