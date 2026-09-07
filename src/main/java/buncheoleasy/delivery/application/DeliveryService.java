package buncheoleasy.delivery.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
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
    validateShippable(buncheol, participation);

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
    validateShippable(
        buncheolDomainService.getBuncheol(adminPathParticipation.getBuncheolId()),
        adminPathParticipation);

    deliveryDomainService.registerTracking(deliveryId, trackingNumber, Instant.now(clock));
    eventPublisher.publishEvent(new TrackingRegisteredEvent(deliveryId));
  }

  /**
   * 발송을 시작해도 되는지 — 플로우마다 규칙이 다르다.
   *
   * <p><b>C2C 는 그 자리의 입금확인만 본다</b> (2026-09-05 사용자 결정 — 다른 자리 상태와 무관).
   * 「이미 보낸 물건이 있는데 분철이 나중에 취소되는」 모순이 C2C 엔 구조적으로 없기 때문이다:
   * 입금확인이 1건이라도 있으면 개최자 취소가 {@code BLOCKED_BY_CONFIRMED_PAYMENT} 로 막히고,
   * {@code PAYMENT_COLLECTING} 의 자동취소({@code cancelIfCollectingAndEmpty})는 <b>활성 참여가
   * 0건일 때만</b> 도는데 입금확인된 참여는 {@code ParticipationStatus.ACTIVE} 에 포함되므로
   * 배송이 있는 분철에는 성립하지 않는다.
   *
   * <p>「배송이 존재한다 = 그 자리는 입금확인됐다」를 암묵 전제로 두지 않고 여기서 직접 확인한다 —
   * 생성 경로가 {@code DeliverySnapshotCreator} 하나라는 사실에 기대면, P4(묶음 단위 배송 승격)나
   * 백필로 구조가 바뀌는 순간 조용히 fail-open 이 된다.
   *
   * <p><b>LEGACY 는 분철 진행확정(CONFIRMED)을 요구한다.</b> 마감 판정에서 최소 인원 미달이면
   * 입금확인된 자리가 있어도 CANCELLED 로 가므로 위 모순이 실재한다. CONFIRMED 는 이후
   * RECRUITING 으로 되돌아가지 않으므로 check-then-act 갭이 없다.
   */
  private void validateShippable(final Buncheol buncheol, final Participation participation) {
    if (buncheol.isC2c()) {
      if (participation.getStatus() != ParticipationStatus.CONFIRMED) {
        throw new BusinessException(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
      }
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
