package buncheoleasy.deposit.application;

import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.application.participation.PaymentExpiredEvent;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.deposit.infrastructure.PayActionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 참여 생명주기를 페이액션 주문에 반영한다. 참여가 접수되면 매칭 대기 주문을 등록하고, 입금 기한이 지나 자동 취소되면 주문을 취소한다 — 취소하지 않으면 취소된
 * 참여에 뒤늦은 입금이 매칭돼 불필요한 웹훅이 발생한다.
 *
 * <p>원 트랜잭션 커밋 후 비동기로 실행되며, 호출 실패는 로깅만 하고 참여 처리에 영향을 주지 않는다. 자동 입금확인은 보조 수단이고 운영자 수동 확인 경로가 그대로
 * 남아 있기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositOrderListener {

  private final PayActionClient payActionClient;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final BuncheolDomainService buncheolDomainService;

  /**
   * 신규 참여 접수 → 매칭 대기 주문 등록. 페이액션은 주문이 입금보다 먼저 도달해야 매칭하므로 커밋 직후 지체 없이 등록한다.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onParticipationCreated(final ParticipationCreatedEvent event) {
    // C2C 는 개최자 개인 계좌 직거래(개최자 수동 확인)라 페이액션 자동확인을 적용하지 않는다 (docs/46 §3-2).
    if (event.flowType() == FlowType.C2C) {
      return;
    }
    if (!payActionClient.isEnabled()) {
      return;
    }
    if (registerQuietly(event.participationId())) {
      cancelIfCancelledMeanwhile(event.participationId());
    }
  }

  private boolean registerQuietly(final Long participationId) {
    try {
      Participation participation = participationDomainService.getParticipation(participationId);
      // 입금 대기 참여만 등록한다. 리스너가 밀린 사이 운영자가 확정한 참여에 주문을 걸면 매칭 없이 dueAt 까지 남아,
      // 같은 예금주·금액의 중복 입금이 ALREADY_CONFIRMED 로 알림 없이 묻힌다. 취소된 참여는 걸 이유가 없다.
      if (participation.getStatus() != ParticipationStatus.AWAITING_PAYMENT) {
        return false;
      }
      // 🔴 묶음을 먼저 읽는다. 아래 두 판정(0원 게이트 · 입금자명)이 같은 묶음을 봐야 한다.
      ParticipationBundle bundle =
          participationBundleDomainService.findByParticipation(participation).orElse(null);
      // 0원 참여는 매칭할 입금이 없다. 판정은 금액으로 한다 — 참여 계좌 강제(PR #151) 이후 0원 참여도 계좌를
      // 가지므로, 계좌 유무로 바꾸면 0원 참여가 페이액션에 등록되어 금액만으로 오매칭될 수 있다(docs/80 §6-4).
      // 🔴 게이트와 등록 금액이 같은 숫자를 봐야 한다. 저장 총액을 쓰면 ① 배송비만 내는 0원 이벤트 참여가
      // 「무료」로 잡혀 등록이 통째로 빠지고 ② 유상 참여는 배송비만큼 적은 금액으로 등록돼 실입금과 어긋나
      // 매칭이 실패한다. 묶음 원값을 더하면 다슬롯에서 곱해지므로 귀속 판정을 쓴다.
      long paymentAmount =
          participationBundleDomainService
              .shippingFeeAttributionOf(bundle, participation.getId())
              .totalAmountOf(participation);
      if (paymentAmount == 0) {
        return false;
      }
      // 입금자명의 정본은 묶음이다 (P2-c). 🔴 없으면 등록을 스킵한다 — 빈 입금자명으로 등록하면 금액만으로
      // 오매칭되어 남의 입금이 남의 참여를 확정시킬 수 있다(docs/80 §6-4). 자동확인만 못 하고 운영자가
      // 슬랙 신규 참여 알림을 보고 수동 확인한다.
      RefundAccount refundAccount = bundle == null ? null : bundle.getRefundAccount();
      if (refundAccount == null) {
        log.warn(
            "묶음 계좌가 없어 페이액션 주문 등록을 건너뛴다 - participationId={}", participation.getId());
        return false;
      }
      payActionClient.registerOrder(
          participation.getId(),
          paymentAmount,
          refundAccount.holder(),
          participation.getCreatedAt(),
          participation.getDueAt());
      return true;
    } catch (RuntimeException e) {
      // 등록 실패 = 자동확인만 불가. 운영자가 슬랙 신규 참여 알림을 보고 수동 확인할 수 있다.
      log.error("페이액션 주문 등록 실패 - participationId={}", participationId, e);
      return false;
    }
  }

  /**
   * 🔴 등록과 취소 리스너는 서로 다른 @Async 스레드라 순서가 보장되지 않는다. 취소가 먼저 도달하면 404 로 끝나고 뒤이어
   * 등록된 주문이 살아남는다. 취소 이벤트는 커밋 후 발행되므로 등록 뒤 재조회로 잡힌다. 입금확인 이력이 있는 참여는
   * {@link #onBuncheolCancelled} 와 같은 이유로 건드리지 않는다.
   */
  private void cancelIfCancelledMeanwhile(final Long participationId) {
    final Participation participation;
    try {
      participation = participationDomainService.getParticipation(participationId);
    } catch (RuntimeException e) {
      // findQuietly 처럼 취소로 폴백하면 안 된다 — 여기엔 취소 신호가 없어 입금을 기다리는 정상 주문까지 취소하게 된다.
      log.error("페이액션 주문 등록 후 참여 상태 재확인 실패 - participationId={}", participationId, e);
      return;
    }
    if (participation.getStatus() == ParticipationStatus.CANCELLED
        && participation.getConfirmedAt() == null) {
      cancelQuietly(participationId);
    }
  }

  /** 입금 기한 만료로 자동 취소됨 → 주문 취소. C2C 는 등록된 주문이 없어 스킵한다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentExpired(final PaymentExpiredEvent event) {
    Participation participation = findQuietly(event.participationId());
    if (participation != null && isC2c(participation)) {
      return;
    }
    cancelQuietly(event.participationId());
  }

  /**
   * 분철 취소 cascade 로 참여가 취소됨 → 주문 취소. 취소하지 않으면 최대 dueAt 까지 주문이 살아 있어, 뒤늦은 입금이 매칭돼 불필요한 알림이 나가거나
   * 같은 사용자가 동일 금액으로 재참여했을 때 옛 주문이 매칭을 가져가 새 참여의 자동확정을 방해할 수 있다.
   *
   * <p>cascade 는 입금확인된 참여도 취소하지만 그 주문은 건드리지 않는다. 이미 매칭이 끝났고, 취소 API 는 결제 취소 성격(취소액·잔액 응답)이라
   * 매칭 완료 주문에 어떤 부수효과가 있는지 확인되지 않았다. 판정은 cascade 가 지우지 않는 {@code confirmedAt} 으로 한다.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolCancelled(final BuncheolCancelledEvent event) {
    Participation participation = findQuietly(event.participationId());
    if (participation != null
        && (participation.getConfirmedAt() != null || isC2c(participation))) {
      return;
    }
    cancelQuietly(event.participationId());
  }

  // 조회 실패는 LEGACY·미확정으로 간주해 취소 경로를 태운다 — 주문이 없으면 취소는 no-op 이다.
  private Participation findQuietly(final Long participationId) {
    try {
      return participationDomainService.getParticipation(participationId);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private boolean isC2c(final Participation participation) {
    try {
      return buncheolDomainService.getBuncheol(participation.getBuncheolId()).isC2c();
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * 주문 취소 실패는 무시한다. 취소되지 않은 주문에 입금이 매칭돼 웹훅이 오더라도 확정 CAS 가 막고 기한 경과 입금으로 운영자에게 알려지므로, 잘못 확정될
   * 위험은 없다.
   */
  private void cancelQuietly(final Long participationId) {
    if (!payActionClient.isEnabled()) {
      return;
    }
    try {
      payActionClient.cancelOrder(participationId);
    } catch (RuntimeException e) {
      // 스택트레이스는 PayActionClient 가 이미 남겼다.
      log.error("페이액션 주문 취소 실패 - participationId={} cause={}", participationId, e.getMessage());
    }
  }
}
