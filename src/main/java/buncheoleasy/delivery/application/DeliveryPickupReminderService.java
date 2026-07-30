package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지점 도착 후 기준 시간이 지나도록 미수령인 배송에 독촉 알림 이벤트를 발행한다. 발송 마킹 CAS 가 dedup 가드라 배송당 1회만 발송된다. 자동 수령완료는
 * 하지 않는다 — 실제 픽업은 운송사 추적이 잡아주고, 미수령 반송 건을 수령완료로 찍으면 데이터가 거짓이 된다.
 */
@Service
public class DeliveryPickupReminderService {

  // 한 주기 처리 상한. 도착 오래된 순 조회라 초과분도 다음 주기에 따라잡힌다.
  private static final int BATCH_SIZE = 200;

  private final DeliveryDomainService deliveryDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Duration threshold;

  public DeliveryPickupReminderService(
      final DeliveryDomainService deliveryDomainService,
      final ApplicationEventPublisher eventPublisher,
      // 인라인 기본값 — 이 빈은 스케줄러가 꺼져 있어도 항상 뜨므로 빈 env 값에 기동이 죽지 않게.
      @Value("${app.delivery.pickup-reminder.threshold:12h}") final Duration threshold) {
    this.deliveryDomainService = deliveryDomainService;
    this.eventPublisher = eventPublisher;
    this.threshold = threshold;
  }

  /** 도착 후 기준 시간이 지난 미독촉 DELIVERED 배송을 최대 {@link #BATCH_SIZE} 개 조회한다. */
  public List<Delivery> findReminderTargets(final Instant now) {
    return deliveryDomainService.findPickupReminderTargets(now.minus(threshold), BATCH_SIZE);
  }

  /** 단일 배송의 독촉 처리 — 마킹 CAS 성공 시에만 이벤트를 발행한다(커밋 후 발송). 이미 독촉·수령된 배송은 false. */
  @Transactional
  public boolean remind(final Long deliveryId, final Instant now) {
    boolean marked = deliveryDomainService.markPickupReminderSent(deliveryId, now);
    if (marked) {
      eventPublisher.publishEvent(new PickupReminderDueEvent(deliveryId));
    }
    return marked;
  }
}
