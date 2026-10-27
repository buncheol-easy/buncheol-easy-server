package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 분철 취소 뒤 연쇄 처리. 개최자 모집 중 삭제, 마감 미달 취소(운영진 분철·회원 개최), 운영진 파기가 같은 순서를 탄다 — 살아 있는 참여를
 * 모두 {@code BUNCHEOL_CANCELLED} 로 → 빈 묶음 닫기 → 실제로 전이된 참여 재조회 → 그 참여의 배송 삭제 → {@link
 * BuncheolCancelledEvent} 1번. {@link BuncheolConfirmedFinalizer} 와 짝이다.
 *
 * <p>호출자가 자기 협력자로 만들어 분철 취소 CAS 와 같은 트랜잭션 안에서 부른다. 입금확인된 참여도 함께 취소되며 환불은 오프라인 처리다.
 */
public record BuncheolCancelledFinalizer(
    ParticipationDomainService participationDomainService,
    ParticipationBundleDomainService participationBundleDomainService,
    DeliveryDomainService deliveryDomainService,
    ApplicationEventPublisher eventPublisher) {

  public void finalizeCancelled(
      final Long buncheolId, final Instant now, final BuncheolCancelReason reason) {
    participationDomainService.cancelActiveByBuncheolId(buncheolId, now);
    // cascade 는 참여를 한 번의 UPDATE 로 전이시켜 어느 묶음이 비었는지 개별로 알 수 없다 — 같은 CAS 조건을
    // 분철 범위로 넓혀 일괄 판정한다. 안 닫으면 취소된 분철에 활성 묶음이 영구히 남는다.
    participationBundleDomainService.closeEmptyByBuncheolId(buncheolId, now);
    // 스냅샷이 아니라 cascade 로 실제 전이된 참여만 재조회해 발행 — 그 사이 자발취소·만료된 참여에 중복 알림이 가지 않도록.
    List<Long> cancelledIds =
        participationDomainService.findCascadeCancelledByBuncheolId(buncheolId).stream()
            .map(Participation::getId)
            .sorted()
            .toList();
    // Delivery 는 취소되지 않은 참여에만 존재해야 한다 — 입금확인 때 생성된 배송 스냅샷을 정리한다.
    deliveryDomainService.deleteByParticipationIds(cancelledIds);
    // 분철 단위로 1번 발행한다 — 참여마다 발행하면 다슬롯 참여자가 취소 알림을 슬롯 수만큼 받는다.
    if (!cancelledIds.isEmpty()) {
      eventPublisher.publishEvent(new BuncheolCancelledEvent(buncheolId, cancelledIds, reason));
    }
  }
}
