package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
  private final DeliveryRepository deliveryRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 입금확인된 참여 전체에 진행확정 알림을 발행한다. 남은 입금확인중(AWAITING_PAYMENT) 참여는 손대지 않는다 — 입금 만료 스케줄러가
   * CANCELLED(PAYMENT_TIMEOUT) 전이·알림을 단독으로 처리해 알림 중복을 막는다.
   */
  public void finalizeConfirmed(final Long buncheolId) {
    List<Participation> confirmed =
        participationDomainService.findConfirmedByBuncheolId(buncheolId);
    // 🔴 운송장이 이미 등록된 묶음은 알림 대상에서 뺀다 (2026-09-06 사용자 결정). C2C 는 진행확정 전에도
    // 입금확인 즉시 운송장을 등록할 수 있어(DeliveryService#validateShippable), 그 참여자는 「발송되었어요」를
    // 이미 받았다 — 그 뒤에 「이제 상품 준비가 시작돼요. 발송되면 운송장과 함께 알려드릴게요」가 가면
    // 시간이 거꾸로 가는 안내가 된다.
    Set<Long> shippedBundleIds =
        deliveryRepository
            .findAllByBundleIds(
                confirmed.stream().map(Participation::getBundleId).filter(Objects::nonNull).toList())
            .stream()
            .filter(delivery -> delivery.getTrackingNumber() != null)
            .map(Delivery::getBundleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    List<Long> confirmedIds =
        confirmed.stream()
            .filter(p -> p.getBundleId() == null || !shippedBundleIds.contains(p.getBundleId()))
            .map(Participation::getId)
            .toList();
    eventPublisher.publishEvent(new BuncheolConfirmedEvent(buncheolId, confirmedIds));
  }
}
