package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 분철 진행확정 후속 처리. 입금확인된(CONFIRMED) 참여에게 진행확정 알림을 발행한다. 배송은 여기서 만들지 않는다 — 운영진 분철은 개별
 * 참여의 입금확인 때, 회원 개최는 진행 확정 때 호출자가 만든다.
 *
 * <p>분철이 CONFIRMED 로 전이되는 경로 — 운영진 분철의 deadline 마감 스케줄러({@code BuncheolAutoCloseService})·전 슬롯 입금확인 시
 * 조기 확정({@code ParticipationService#confirmPayment}), 회원 개최의 진행 확정({@code BuncheolService#confirmBuncheol}) —
 * 이 동일한 후속 처리를 공유한다. 호출 측 {@code @Transactional} 안에서 호출해야 한다.
 */
@Component
@RequiredArgsConstructor
public class BuncheolConfirmedFinalizer {

  private final ParticipationDomainService participationDomainService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 입금확인된 참여 전체에 진행확정 알림을 발행한다. 남은 입금확인중(AWAITING_PAYMENT) 참여는 손대지 않는다 — 운영진 분철은 입금 만료
   * 스케줄러가 CANCELLED(PAYMENT_TIMEOUT) 전이·알림을 단독으로 처리해 알림 중복을 막고, 회원 개최는 진행 확정이 같은 트랜잭션에서 뺀다.
   */
  public void finalizeConfirmed(final Long buncheolId) {
    List<Long> confirmedIds =
        participationDomainService.findConfirmedByBuncheolId(buncheolId).stream()
            .map(Participation::getId)
            .toList();
    eventPublisher.publishEvent(new BuncheolConfirmedEvent(buncheolId, confirmedIds));
  }
}
