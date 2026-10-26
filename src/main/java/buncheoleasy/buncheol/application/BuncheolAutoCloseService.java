package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
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
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  private final PaymentStarter paymentStarter;
  private final DeliveryDomainService deliveryDomainService;
  private final ApplicationEventPublisher eventPublisher;

  /** {@code now} 기준 마감이 지난 RECRUITING 분철 id 를 최대 {@link #BATCH_SIZE} 개 조회한다. */
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
    // 회원 개최는 마감 판정 규칙이 다르다 — 신청(무입금) 인원으로 입금 단계를 시작하거나 미달 취소한다. 분기 없이 운영진
    // 판정(입금확인 인원)을 태우면 전원 무입금 신청(APPLIED)이라 정각에 전부 미달 취소돼 버린다.
    Buncheol target = buncheolDomainService.getBuncheol(buncheolId);
    if (target.isC2c()) {
      return finalizeExpiredC2c(buncheolId, now);
    }

    // 입금확인 인원 카운트·최소 인원 비교·상태 전이를 단일 CAS(CASE+서브쿼리)로 원자화한다. 카운트를 별도 SELECT 로 먼저 읽으면
    // 그 사이 호스트의 입금확인 커밋으로 stale count 가 되어 충족인데 CANCELLED 로 오판할 수 있다.
    if (!buncheolDomainService.finalizeExpiredByConfirmedHeadcount(buncheolId, now)) {
      // 다른 인스턴스가 이미 마감했거나 그 사이 상태가 RECRUITING 이 아니게 됨.
      return false;
    }

    // CAS 가 확정한 실제 상태를 재조회해 후속 처리를 분기한다 (@Modifying clearAutomatically 로 1차 캐시가 비워진 뒤의 fresh read).
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    if (buncheol.getStatus() == BuncheolStatus.CONFIRMED) {
      finalizeAsConfirmed(buncheolId);
    } else {
      finalizeAsCancelled(buncheolId, now, BuncheolCancelReason.MIN_HEADCOUNT_NOT_MET);
    }
    return true;
  }

  /**
   * 회원 개최 마감 판정. 신청 자리가 최소 인원 이상이면 입금 단계를 시작하고({@link PaymentStarter}), 미달이면 취소하고 신청자 전원에게
   * 알린다. 미달 취소는 돈이 오간 적 없는 단계라 환불이 없다. 시작과 미달 취소를 한 트랜잭션, 분철 행 잠금 아래에서 판정한다.
   */
  private boolean finalizeExpiredC2c(final Long buncheolId, final Instant now) {
    // 분철 행을 잠그고 최신 값으로 다시 읽는다 — 첫 조회(finalizeExpired)는 잠금 전이라 그 사이 신청·마감 연장·삭제를 못 본다.
    Buncheol buncheol = buncheolDomainService.getLatestBuncheolForUpdate(buncheolId);
    // 신청자 0명이면 개최자가 마감을 연장할 수 있다 — 폴링이 옛 마감으로 골랐어도 지금 마감이 안 지났으면 판정하지 않는다.
    if (buncheol.getStatus() != BuncheolStatus.RECRUITING || buncheol.getDeadline().isAfter(now)) {
      return false;
    }
    if (paymentStarter.startAtDeadline(buncheol, now)) {
      return true;
    }
    if (!buncheolDomainService.cancelBelowMinHeadcount(buncheolId, now)) {
      // 잠금 아래라 오면 안 되는 경우다. 참여·알림은 건드리지 않고 다음 폴링에 맡긴다.
      return false;
    }
    finalizeAsCancelled(buncheolId, now, BuncheolCancelReason.MIN_HEADCOUNT_NOT_MET);
    return true;
  }

  // 진행확정: 입금확인된 참여로 배송 스냅샷 생성 + 진행확정 알림. 남은 입금확인중 참여(마감 시점엔 모두 입금 기한 도과)는 여기서 손대지 않는다 —
  // 입금 만료 스케줄러가 폴링 주기 내에 CANCELLED(PAYMENT_TIMEOUT) 전이 + 자동취소 알림을 단독으로 처리한다(두 경로가 같은 참여에 알림을
  // 중복 발송하지 않도록 만료 책임을 스케줄러로 일원화). 마감 임박 참여의 컷오프 위험은 참여 시 프론트 안내로 사전 고지한다.
  // 전 슬롯 조기 확정(ParticipationService.confirmPayment)과 동일한 후속 처리를 공유한다.
  private void finalizeAsConfirmed(final Long buncheolId) {
    buncheolConfirmedFinalizer.finalizeConfirmed(buncheolId);
  }

  // 미달 취소: 활성 참여 전체를 CANCELLED(BUNCHEOL_CANCELLED) 로 취소하고 참여자에게 사유와 함께 취소 알림을 보낸다.
  // 운영진 분철은 입금확인 인원, 회원 개최는 신청 인원이 최소 인원에 못 미친 경우다. 입금확인된 참여의 환불은 오프라인 처리.
  private void finalizeAsCancelled(
      final Long buncheolId, final Instant now, final BuncheolCancelReason reason) {
    participationDomainService.cancelActiveByBuncheolId(buncheolId, now);
    // 취소된 참여의 묶음도 함께 닫는다 (개최자 취소 경로와 같은 이유 — BuncheolService#cancelBuncheol).
    participationBundleDomainService.closeEmptyByBuncheolId(buncheolId, now);
    // 스냅샷이 아니라 cascade 로 실제 전이된 참여만 재조회해 발행 — 그 사이 자발취소·만료된 참여에 중복 알림이 가지 않도록.
    List<Long> cancelledIds =
        participationDomainService.findCascadeCancelledByBuncheolId(buncheolId).stream()
            .map(Participation::getId)
            .sorted()
            .toList();
    // 입금확인 시 생성된 배송 스냅샷을 정리한다 — Delivery 는 취소되지 않은 참여에만 존재해야 한다.
    deliveryDomainService.deleteByParticipationIds(cancelledIds);
    // 분철 단위로 1번 발행한다 — 참여마다 발행하면 다슬롯 참여자가 취소 알림을 슬롯 수만큼 받는다.
    if (!cancelledIds.isEmpty()) {
      eventPublisher.publishEvent(new BuncheolCancelledEvent(buncheolId, cancelledIds, reason));
    }
  }
}
