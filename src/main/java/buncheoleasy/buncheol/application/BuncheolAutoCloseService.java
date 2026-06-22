package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuncheolAutoCloseService {

  // 한 폴링 주기에 처리할 분철 수 상한. 마감이 몰려도 트랜잭션·조회 부하를 제한하고, 남은 분철은 다음 주기에 처리한다.
  private static final int BATCH_SIZE = 100;

  private final BuncheolRepository buncheolRepository;
  private final BuncheolDomainService buncheolDomainService;
  private final ParticipationDomainService participationDomainService;
  private final DeliverySnapshotCreator deliverySnapshotCreator;
  private final ApplicationEventPublisher eventPublisher;

  /** {@code now} 기준 deadline 이 지난 RECRUITING 분철 id 를 최대 {@link #BATCH_SIZE} 개 조회한다. */
  public List<Long> findExpiredBuncheolIds(final Instant now) {
    return buncheolRepository.findRecruitingIdsPastDeadline(now, BATCH_SIZE);
  }

  /**
   * deadline 이 지난 단일 분철의 마감 판정. 입금확인된(CONFIRMED) 참여자가 최소 인원 이상이면 진행확정, 미만이면 취소한다. {@code RECRUITING
   * → CONFIRMED/CANCELLED} CAS 로 선점에 성공한 인스턴스만 후속 처리를 수행해 다중 인스턴스 중복 마감을 막는다. 판정·후속 처리를 한 트랜잭션으로
   * 묶어, 도중 실패 시 모두 롤백되어 다음 주기에 재시도된다.
   *
   * @return 이 호출이 마감 판정을 수행했으면 {@code true}, 이미 마감됐거나 RECRUITING 이 아니면 {@code false}
   */
  @Transactional
  public boolean finalizeExpired(final Long buncheolId, final Instant now) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    int confirmedCount = participationDomainService.countConfirmedByBuncheolId(buncheolId);
    boolean proceed = confirmedCount >= buncheol.getMinHeadcount();
    BuncheolStatus target = proceed ? BuncheolStatus.CONFIRMED : BuncheolStatus.CANCELLED;

    if (!buncheolDomainService.finalizeBuncheol(buncheolId, target, now)) {
      // 다른 인스턴스가 이미 마감했거나 그 사이 상태가 RECRUITING 이 아니게 됨.
      return false;
    }

    if (proceed) {
      finalizeAsConfirmed(buncheolId);
    } else {
      finalizeAsCancelled(buncheolId, now);
    }
    return true;
  }

  // 진행확정: 입금확인된 참여로 배송 스냅샷 생성 + 진행확정 알림. 남은 입금확인중 참여(마감 시점엔 모두 입금 기한 도과)는 여기서 손대지 않는다 —
  // 입금 만료 스케줄러가 폴링 주기 내에 CANCELLED(PAYMENT_TIMEOUT) 전이 + 자동취소 알림을 단독으로 처리한다(두 경로가 같은 참여에 알림을
  // 중복 발송하지 않도록 만료 책임을 스케줄러로 일원화). 마감 임박 참여의 컷오프 위험은 참여 시 프론트 안내로 사전 고지한다.
  private void finalizeAsConfirmed(final Long buncheolId) {
    List<Participation> confirmed =
        participationDomainService.findConfirmedByBuncheolId(buncheolId);
    confirmed.forEach(
        participation -> {
          deliverySnapshotCreator.create(participation);
          eventPublisher.publishEvent(new BuncheolConfirmedEvent(participation.getId()));
        });
  }

  // 최소 인원 미달 취소: 활성 참여 전체를 CANCELLED(BUNCHEOL_CANCELLED) 로 취소하고 참여자에게 취소 알림을 보낸다.
  // 입금확인된 참여의 환불은 운영자가 오프라인으로 처리한다.
  private void finalizeAsCancelled(final Long buncheolId, final Instant now) {
    List<Participation> active = participationDomainService.findActiveByBuncheolId(buncheolId);
    participationDomainService.cancelActiveByBuncheolId(buncheolId, now);
    active.forEach(
        participation ->
            eventPublisher.publishEvent(
                new BuncheolCancelledEvent(
                    participation.getId(), BuncheolCancelReason.MIN_HEADCOUNT_NOT_MET)));
  }
}
