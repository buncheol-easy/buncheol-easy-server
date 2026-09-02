package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import java.time.Duration;
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

  // C2C 확정 유예 — 신청 마감(deadline) 후 이 시간 안에 개최자가 확정하지 않으면 미성사 자동 취소한다 (docs/46 §7.1-5).
  private static final Duration C2C_CONFIRM_GRACE = Duration.ofHours(48);

  private final BuncheolRepository buncheolRepository;
  private final BuncheolDomainService buncheolDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  private final DeliveryDomainService deliveryDomainService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * {@code now} 기준 마감 판정 대상 RECRUITING 분철 id 를 최대 {@link #BATCH_SIZE} 개 조회한다. C2C 는 확정
   * 유예(48h)까지 지난 분철만 대상이다 — 유예 중 분철이 배치를 잠식하지 않게 쿼리에서 거른다.
   */
  public List<Long> findExpiredBuncheolIds(final Instant now) {
    return buncheolRepository.findRecruitingIdsPastDeadline(
        now, now.minus(C2C_CONFIRM_GRACE), BATCH_SIZE);
  }

  /** {@code now} 기준 일괄 입금 기한이 지난 PAYMENT_COLLECTING 분철 id 를 최대 {@link #BATCH_SIZE} 개 조회한다. */
  public List<Long> findOverdueCollectingIds(final Instant now) {
    return buncheolRepository.findCollectingIdsPastPaymentDue(now, BATCH_SIZE);
  }

  /**
   * C2C 입금 수집 데드엔드 정리 (docs/46 §7.1-6 보완). 기한이 지났고 활성 참여가 하나도 남지 않았으면 미성사
   * 취소한다 — 개최자가 선택할 것(부분 확정/취소)이 없는 유일한 케이스라 자동화해도 §7.1-6 과 충돌하지 않는다.
   *
   * <p>⚠️ <b>C2C 자동 만료를 끈 뒤로는 이 조건이 잘 성립하지 않는다.</b> 예전에는 만료 스케줄러가 미입금 슬롯을
   * 치워 줘서 활성이 저절로 0 이 됐지만, 이제는 개최자가 「제외」를 눌러야 빈다. <b>개최자가 아무것도 안 하면
   * 분철이 끝나지 않는데, 이는 「제외」가 정문을 만들면서 수용하기로 한 트레이드오프다</b>(docs/70 §1, 사용자
   * 결정). 자동으로 접지 않는다.
   *
   * <p>🟡 <b>그래서 그런 분철이 폴링 배치를 잠식한다</b> — 안 죽는 분철이 앞자리를 영구 점유해 뒤에 온 분철의
   * 정리가 굶는다. 조회에서 걸러 내지 <b>않는다</b>(prod C2C 「입금 수집중」 0 건이라 당장 발현하지 않아
   * 수용한 한계 — docs/82 §6 · {@code JpaBuncheolRepository#findIdsByStatusAndPaymentDueBefore}).
   *
   * <p>참여자 알림은 없다 — 전원이 이미 개별 취소 안내를 받은 뒤다.
   */
  @Transactional
  public boolean cancelDeadCollecting(final Long buncheolId, final Instant now) {
    return buncheolDomainService.cancelCollectingIfEmpty(buncheolId, now);
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
    // C2C 는 마감 판정 규칙이 다르다 — deadline 은 신청 마감일 뿐이고, 확정 유예(48h) 안에는 개최자의 성사 확정을
    // 기다린다. 분기 없이 LEGACY 판정을 태우면 전원 무입금 신청(APPLIED)이라 정각에 전부 미달 취소돼 버린다 (docs/46 §1.2).
    Buncheol target = buncheolDomainService.getBuncheol(buncheolId);
    if (target.isC2c()) {
      return finalizeExpiredC2c(target, now);
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
   * C2C 방치 분철 정리 (docs/46 §7.1-5). deadline+48h 유예 안에는 손대지 않고(개최자 확정 대기 — 확정되면
   * PAYMENT_COLLECTING 으로 바뀌어 폴링 대상에서 빠진다), 유예가 지나면 미성사 취소(CANCELLED)하고 신청자 전원에게 취소를 알린다.
   * 돈이 오간 적 없는 단계라 환불은 발생하지 않는다.
   */
  private boolean finalizeExpiredC2c(final Buncheol buncheol, final Instant now) {
    // 🔴 최소 인원 미달이면 유예를 기다리지 않는다 (2026-08-31 사용자 결정). 개최자가 확정을 눌러도 인원이
    // 채워지지 않는 분철을 48시간 붙잡아 두면, 참여자는 그동안 자기 자리가 살아 있는 줄 안다. LEGACY 가 마감
    // 시각에 인원 미달로 바로 취소하는 것과 같은 규칙이다.
    //
    // 신청 인원으로 센다 — C2C 는 확정 전이라 입금확인 건이 0 이다(LEGACY 는 CONFIRMED 로 센다).
    boolean belowMinHeadcount =
        participationDomainService.findActiveByBuncheolId(buncheol.getId()).size()
            < buncheol.getMinHeadcount();
    // 폴링 쿼리가 대상만 주지만, cron 경계·수동 호출 대비 이중 방어로 조건을 재확인한다.
    if (!belowMinHeadcount
        && now.isBefore(buncheol.getDeadline().plus(C2C_CONFIRM_GRACE))) {
      return false;
    }
    if (!buncheolDomainService.cancelUnconfirmedC2c(buncheol.getId(), now)) {
      return false; // 그 사이 확정·취소됨 (CAS 경합).
    }
    // 사유를 가른다 — 참여자에게 "인원이 모이지 않았어요" 와 "개최자가 확정하지 않았어요" 는 다른 안내다.
    finalizeAsCancelled(
        buncheol.getId(),
        now,
        belowMinHeadcount
            ? BuncheolCancelReason.MIN_HEADCOUNT_NOT_MET
            : BuncheolCancelReason.NOT_FINALIZED);
    return true;
  }

  // 진행확정: 입금확인된 참여로 배송 스냅샷 생성 + 진행확정 알림. 남은 입금확인중 참여(마감 시점엔 모두 입금 기한 도과)는 여기서 손대지 않는다 —
  // 입금 만료 스케줄러가 폴링 주기 내에 CANCELLED(PAYMENT_TIMEOUT) 전이 + 자동취소 알림을 단독으로 처리한다(두 경로가 같은 참여에 알림을
  // 중복 발송하지 않도록 만료 책임을 스케줄러로 일원화). 마감 임박 참여의 컷오프 위험은 참여 시 프론트 안내로 사전 고지한다.
  // 전 슬롯 조기 확정(ParticipationService.confirmPayment)과 동일한 후속 처리를 공유한다.
  private void finalizeAsConfirmed(final Long buncheolId) {
    buncheolConfirmedFinalizer.finalizeConfirmed(buncheolId);
  }

  // 미성사 취소: 활성 참여 전체를 CANCELLED(BUNCHEOL_CANCELLED) 로 취소하고 참여자에게 사유와 함께 취소 알림을 보낸다.
  // LEGACY 는 최소 인원 미달, C2C 는 개최자 미확정 마감(NOT_FINALIZED)이 사유다. 입금확인된 참여의 환불은 오프라인 처리.
  private void finalizeAsCancelled(
      final Long buncheolId, final Instant now, final BuncheolCancelReason reason) {
    participationDomainService.cancelActiveByBuncheolId(buncheolId, now);
    // 취소된 참여의 묶음도 함께 닫는다 (개최자 취소 경로와 같은 이유 — BuncheolService#cancelBuncheol).
    participationBundleDomainService.closeEmptyByBuncheolId(buncheolId, now);
    // 스냅샷이 아니라 cascade 로 실제 전이된 참여만 재조회해 발행 — 그 사이 자발취소·만료된 참여에 중복 알림이 가지 않도록.
    List<Participation> cancelled =
        participationDomainService.findCascadeCancelledByBuncheolId(buncheolId);
    // 입금확인 시 생성된 배송 스냅샷을 정리한다 — Delivery 는 취소되지 않은 참여에만 존재해야 한다.
    deliveryDomainService.deleteByParticipationIds(
        cancelled.stream().map(Participation::getId).toList());
    cancelled.forEach(
        participation ->
            eventPublisher.publishEvent(
                new BuncheolCancelledEvent(participation.getId(), reason)));
  }
}
