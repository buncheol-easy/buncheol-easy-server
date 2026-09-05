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

  /**
   * 발송을 시작해도 되는지. <b>LEGACY 만 분철 진행확정(CONFIRMED)을 요구한다.</b>
   *
   * <p>이 가드가 막으려던 것은 "이미 보낸 물건이 있는데 분철이 나중에 취소되는" 모순이다. LEGACY 는 마감 판정에서
   * 최소 인원에 미달하면 입금확인된 자리가 있어도 CANCELLED 로 가므로 그 위험이 실재한다.
   *
   * <p>🔴 <b>C2C 에서는 그 위험이 구조적으로 없다.</b> 입금확인이 1건이라도 있으면 개최자 취소가 {@code
   * BLOCKED_BY_CONFIRMED_PAYMENT} 로 막히고(직거래라 확인된 돈은 이미 개최자 계좌에 있다), {@code
   * PAYMENT_COLLECTING} 에는 자동취소 경로가 없다. 배송 스냅샷은 입금확인 시점에만 생기므로 <b>운송장을 넣을 수
   * 있는 배송이 존재한다는 것 자체가 분철 취소가 막혔다는 뜻</b>이다.
   *
   * <p>그래서 C2C 는 분철 전체가 아니라 <b>그 자리의 입금확인</b>만 본다. 분철 상태에 묶어 두면 다른 자리가 아직
   * 입금 전이라는 이유로 이미 돈을 낸 사람의 배송이 잠기고, 추가 모집 참여자가 한 명 들어오는 순간 등록할 수 있던
   * 운송장이 다시 잠긴다 (2026-09-05 사용자 결정).
   */
  private void validateBuncheolConfirmed(final Buncheol buncheol) {
    if (buncheol.isC2c()) {
      return;
    }
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
