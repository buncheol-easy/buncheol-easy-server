package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 분철 진행확정 후속 처리. 입금확인된(CONFIRMED) 참여에게 진행확정 알림을 발행한다. 배송 스냅샷은 진행확정이 아니라 개별 참여의 입금확인 시점({@code
 * ParticipationService#confirmPayment})에 이미 생성되므로 여기서는 만들지 않는다.
 *
 * <p>분철이 CONFIRMED 로 전이되는 두 경로 — deadline 마감 스케줄러({@code BuncheolAutoCloseService})와 전 슬롯 입금확인 시
 * 조기 확정({@code ParticipationService#confirmPayment}) — 가 동일한 후속 처리를 공유하도록 추출했다. 호출 측 {@code
 * @Transactional} 안에서 호출해야 한다.
 */
@Component
@RequiredArgsConstructor
public class BuncheolConfirmedFinalizer {

  private final ParticipationDomainService participationDomainService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 입금확인된 참여 전체에 진행확정 알림을 발행한다. 남은 입금확인중(AWAITING_PAYMENT) 참여는 손대지 않는다 — 입금 만료 스케줄러가
   * CANCELLED(PAYMENT_TIMEOUT) 전이·알림을 단독으로 처리해 알림 중복을 막는다.
   */
  public void finalizeConfirmed(final Long buncheolId) {
    List<Long> confirmedIds =
        participationDomainService.findConfirmedByBuncheolId(buncheolId).stream()
            .map(Participation::getId)
            .toList();
    eventPublisher.publishEvent(new BuncheolConfirmedEvent(buncheolId, confirmedIds));
  }
}
