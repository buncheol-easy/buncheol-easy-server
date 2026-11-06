package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.application.participation.PaymentNotConfirmedEvent;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 회원 개최 진행 확정 (PAYMENT_COLLECTING → CONFIRMED). 미확인 참여(입금 대기·「보냈어요」)를 {@code PAYMENT_NOT_CONFIRMED} 로
 * 빼고, 빈 묶음을 닫고, 확인된 묶음마다 배송을 만들고, 진행 확정·빠짐 알림을 낸다. 판정(진행 확정 가드, 화면 목록 대조)은 하지 않는다 —
 * 호출자 몫이다.
 *
 * <p>🔴 호출자 계약: 분철 행을 첫 잠금으로 쥐고({@link BuncheolDomainService#getBuncheolForUpdate}), 살아 있는 참여를 그 뒤 FOR
 * UPDATE 로 읽어 {@code active} 로 넘기고, READ COMMITTED 트랜잭션에서만 부른다. 진행확정 알림 대상({@link
 * BuncheolConfirmedFinalizer})과 묶음 배송 유무({@link DeliverySnapshotCreator})를 일반 조회로 읽으므로, 잠금 앞 스냅샷이 남으면
 * 기다리는 사이 커밋된 입금 확인을 빠뜨린다.
 */
@Component
@RequiredArgsConstructor
public class BuncheolConfirmer {

  private final BuncheolDomainService buncheolDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final DeliverySnapshotCreator deliverySnapshotCreator;
  private final BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  private final ApplicationEventPublisher eventPublisher;

  /** @param active 분철 행 잠금 뒤 FOR UPDATE 로 읽은 살아 있는 참여 */
  public void confirm(final Long buncheolId, final List<Participation> active, final Instant now) {
    List<Participation> confirmed =
        active.stream().filter(p -> p.getStatus() == ParticipationStatus.CONFIRMED).toList();
    List<Long> unconfirmedIds =
        active.stream()
            .filter(p -> ParticipationStatus.payableStatuses().contains(p.getStatus()))
            .map(Participation::getId)
            .sorted()
            .toList();

    if (!buncheolDomainService.confirmIfCollecting(buncheolId, now)) {
      // 분철 행을 잠근 채 상태를 봤으므로 여기 오면 안 된다 — 롤백한다.
      throw new BusinessException(ErrorCode.BUNCHEOL_CONFIRM_NOT_ALLOWED);
    }
    if (participationDomainService.cancelUnconfirmedByBuncheolId(buncheolId, now)
        != unconfirmedIds.size()) {
      // 참여 행을 잠근 채 셌으므로 내부 불변식 위반이다 — 롤백해 일부만 빠진 진행 확정을 남기지 않는다.
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    // 빠진 참여만 있던 묶음을 닫는다. 확인된 묶음은 그대로 열려 배송으로 이어진다.
    participationBundleDomainService.closeEmptyByBuncheolId(buncheolId, now);
    // 확인된 묶음마다 배송 1개 — 묶음에 배송이 이미 있으면(옛 서버가 입금 확인 때 만든 것) 건너뛴다.
    confirmed.forEach(deliverySnapshotCreator::create);

    buncheolConfirmedFinalizer.finalizeConfirmed(buncheolId);
    if (!unconfirmedIds.isEmpty()) {
      eventPublisher.publishEvent(new PaymentNotConfirmedEvent(buncheolId, unconfirmedIds));
    }
  }
}
