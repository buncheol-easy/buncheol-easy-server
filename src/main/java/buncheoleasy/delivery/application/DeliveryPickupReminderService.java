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
 * 지점 도착(DELIVERED) 후 기준 시간이 지나도록 미수령인 배송에 독촉 알림 이벤트를 발행한다. 건별 독립 트랜잭션이라 한 건 실패가 나머지를 막지 않고,
 * 발송 마킹 CAS({@code pickupReminderSentAt IS NULL})가 dedup 가드라 다중 인스턴스·재실행에도 1회만 발송된다.
 *
 * <p>자동 수령완료 처리는 하지 않는다 — 실제 픽업은 운송사 추적이 잡아주고, 미수령 반송 건을 수령완료로 찍으면 데이터가 거짓이 된다.
 */
@Service
public class DeliveryPickupReminderService {

  // 한 폴링 주기에 처리할 배송 수 상한. 초과분은 다음 주기에 처리한다 (도착 오래된 순 조회라 굶지 않는다).
  private static final int BATCH_SIZE = 200;

  private final DeliveryDomainService deliveryDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Duration threshold;

  public DeliveryPickupReminderService(
      final DeliveryDomainService deliveryDomainService,
      final ApplicationEventPublisher eventPublisher,
      // 스케줄러가 꺼져 있어도 이 빈은 항상 뜨므로, 키가 빈 값이어도 기동이 죽지 않게 인라인 기본값을 둔다.
      @Value("${app.delivery.pickup-reminder.threshold:12h}") final Duration threshold) {
    this.deliveryDomainService = deliveryDomainService;
    this.eventPublisher = eventPublisher;
    this.threshold = threshold;
  }

  /** {@code now} 기준 도착 후 기준 시간이 지난 미독촉 DELIVERED 배송을 최대 {@link #BATCH_SIZE} 개 조회한다. */
  public List<Delivery> findReminderTargets(final Instant now) {
    return deliveryDomainService.findPickupReminderTargets(now.minus(threshold), BATCH_SIZE);
  }

  /**
   * 단일 배송의 독촉 처리. 마킹 CAS 성공 시에만 알림 이벤트를 발행한다(커밋 후 발송) — 그 사이 수령됐거나 이미 독촉한 배송은 CAS 에 막혀 false 를
   * 돌려준다.
   */
  @Transactional
  public boolean remind(final Long deliveryId, final Instant now) {
    boolean marked = deliveryDomainService.markPickupReminderSent(deliveryId, now);
    if (marked) {
      eventPublisher.publishEvent(new PickupReminderDueEvent(deliveryId));
    }
    return marked;
  }
}
