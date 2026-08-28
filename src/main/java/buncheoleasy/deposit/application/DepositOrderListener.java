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
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.deposit.infrastructure.PayActionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 참여 생명주기를 페이액션 주문에 반영한다. 참여가 접수되면 매칭 대기 주문을 등록하고, 입금 기한이 지나 자동 취소되면 매칭 대기를 해제한다 — 해제하지 않으면 취소된
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
    try {
      Participation participation =
          participationDomainService.getParticipation(event.participationId());
      // 0원 참여는 매칭할 입금이 없다. 판정은 금액(isFree)으로 한다 — 참여 계좌 강제(PR #151) 이후 0원 참여도 계좌를
      // 가지므로, 계좌 유무로 바꾸면 0원 참여가 페이액션에 등록되어 금액만으로 오매칭될 수 있다(docs/80 §6-4).
      if (participation.isFree()) {
        return;
      }
      // 입금자명의 정본은 묶음이다 (P2-c). 🔴 없으면 등록을 스킵한다 — 빈 입금자명으로 등록하면 금액만으로
      // 오매칭되어 남의 입금이 남의 참여를 확정시킬 수 있다(docs/80 §6-4). 자동확인만 못 하고 운영자가
      // 슬랙 신규 참여 알림을 보고 수동 확인한다.
      RefundAccount refundAccount =
          participationBundleDomainService
              .findByParticipation(participation)
              .map(ParticipationBundle::getRefundAccount)
              .orElse(null);
      if (refundAccount == null) {
        log.warn(
            "묶음 계좌가 없어 페이액션 주문 등록을 건너뛴다 - participationId={}", participation.getId());
        return;
      }
      payActionClient.registerOrder(
          participation.getId(),
          participation.getTotalAmount(),
          refundAccount.holder(),
          participation.getCreatedAt(),
          participation.getDueAt());
    } catch (RuntimeException e) {
      // 등록 실패 = 자동확인만 불가. 운영자가 슬랙 신규 참여 알림을 보고 수동 확인할 수 있다.
      log.error("페이액션 주문 등록 실패 - participationId={}", event.participationId(), e);
    }
  }

  /** 입금 기한 만료로 자동 취소됨 → 매칭 대기 해제. C2C 는 등록된 주문이 없어 스킵한다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentExpired(final PaymentExpiredEvent event) {
    if (isC2cParticipation(event.participationId())) {
      return;
    }
    excludeQuietly(event.participationId());
  }

  /**
   * 분철 취소 cascade 로 참여가 취소됨 → 매칭 대기 해제. 해제하지 않으면 최대 dueAt 까지 주문이 살아 있어, 뒤늦은 입금이 매칭돼 불필요한 알림이 나가거나
   * 같은 사용자가 동일 금액으로 재참여했을 때 옛 주문이 매칭을 가져가 새 참여의 자동확정을 방해할 수 있다.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBuncheolCancelled(final BuncheolCancelledEvent event) {
    if (isC2cParticipation(event.participationId())) {
      return;
    }
    excludeQuietly(event.participationId());
  }

  // C2C 참여 여부 조회. 실패하면 LEGACY 로 간주해 기존 해제 경로를 태운다 — 주문이 없으면 해제도 무해한 no-op 이다.
  private boolean isC2cParticipation(final Long participationId) {
    try {
      Participation participation =
          participationDomainService.getParticipation(participationId);
      return buncheolDomainService.getBuncheol(participation.getBuncheolId()).isC2c();
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * 매칭 대기 해제 실패는 무시한다. 해제되지 않은 주문에 입금이 매칭돼 웹훅이 오더라도 확정 CAS 가 막고 기한 경과 입금으로 운영자에게 알려지므로, 잘못 확정될
   * 위험은 없다.
   */
  private void excludeQuietly(final Long participationId) {
    if (!payActionClient.isEnabled()) {
      return;
    }
    try {
      payActionClient.excludeOrder(participationId);
    } catch (RuntimeException e) {
      log.error("페이액션 매칭제외 실패 - participationId={}", participationId, e);
    }
  }
}
