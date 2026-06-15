package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개최자(host) 의 결제 관련 액션. 계좌이체 MVP 에서는 수동 입금확인을 제공한다. */
@Service
@RequiredArgsConstructor
public class HostPaymentService {

  private final ParticipationDomainService participationDomainService;
  private final BuncheolDomainService buncheolDomainService;
  private final DeliverySnapshotCreator deliverySnapshotCreator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 개최자의 수동 입금확인. 구매자가 신고한 참여(PAYMENT_REPORTED)를 개최자가 실제 입금 확인 후 CONFIRMED 로 전환하고 배송 스냅샷을 생성한다. PG
   * 결제 완료 경로(onPaymentCompleted, completePayment=AWAITING_PAYMENT 전제)와 분리된 계좌이체 전용 경로다.
   */
  @Transactional
  public void confirmPayment(final Long hostId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    buncheol.validateOwner(hostId);

    participation.confirmManualPayment(Instant.now(clock));
    deliverySnapshotCreator.create(participation);
    eventPublisher.publishEvent(new PaymentConfirmedEvent(participationId));
  }

  /**
   * 미입금 낙찰자 만료 처리. 입금 기한이 지난 AWAITING_PAYMENT 낙찰자를 FAILED 로 전이하고 같은 멤버 슬롯의 차순위 후보를 입금대기로 승계한다.
   * PAYMENT_REPORTED/CONFIRMED 는 만료 대상이 아니며, 기한 전 호출도 거부된다.
   */
  @Transactional
  public void expirePayment(final Long hostId, final Long participationId) {
    Participation winner = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(winner.getBuncheolId());
    buncheol.validateOwner(hostId);

    // 차순위가 승계되면 새 낙찰자에게 낙찰 알림을 보낸다(케이스1: 차순위 이양으로 새로 낙찰).
    participationDomainService
        .expireWinnerAndPromoteNext(winner, Instant.now(clock))
        .ifPresent(
            promoted -> eventPublisher.publishEvent(new ParticipationWonEvent(promoted.getId())));
  }
}
