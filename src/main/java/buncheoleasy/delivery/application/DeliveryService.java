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
    Participation participation =
        participationDomainService.getParticipation(delivery.getParticipationId());
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    buncheol.validateOwner(hostId);
    validateShippable(buncheol);

    // 웹훅 자동 전이(DELIVERED/RECEIVED)와 겹칠 수 있으므로 전이는 CAS 로만 한다. 위에서 조회한
    // 엔티티는 검증용 데이터 홀더일 뿐 in-memory 로 바꾸지 않는다 (더티체킹 + CAS 혼용 금지).
    deliveryDomainService.registerTracking(deliveryId, trackingNumber, Instant.now(clock));
    eventPublisher.publishEvent(new TrackingRegisteredEvent(deliveryId));
  }

  /** 관리자(운영자)의 운송장 등록. 개최자 소유권 검증 없이 모든 배송의 운송장을 등록할 수 있다는 점만 다르다. */
  @Transactional
  public void registerTrackingByAdmin(final Long deliveryId, final String trackingNumber) {
    Delivery delivery = deliveryDomainService.getDelivery(deliveryId);

    Participation adminPathParticipation =
        participationDomainService.getParticipation(delivery.getParticipationId());
    validateShippable(buncheolDomainService.getBuncheol(adminPathParticipation.getBuncheolId()));

    deliveryDomainService.registerTracking(deliveryId, trackingNumber, Instant.now(clock));
    eventPublisher.publishEvent(new TrackingRegisteredEvent(deliveryId));
  }

  /**
   * 발송을 시작해도 되는지 — 운영진 분철과 회원 개최 모두 <b>분철 진행확정(CONFIRMED)</b>을 요구한다. 회원 개최는 배송이 진행 확정
   * 때 생기고 그 전에 입금 단계 분철이 운영진 파기로 취소될 수 있어, 진행 확정 전 발송은 「보낸 물건이 있는데 분철이 취소되는」
   * 모순을 만든다. 회원 개최 CONFIRMED 는 운영진 파기(운송장 전)로 취소될 수 있다. 파기는 배송 행을 잠그고 지우므로, 이 검사 뒤
   * 파기가 먼저 커밋되면 아래 운송장 CAS 가 0행으로 실패한다(check-then-act 갭은 배송 행 CAS 가 닫는다).
   */
  private void validateShippable(final Buncheol buncheol) {
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
