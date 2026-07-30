package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerCarriers;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 추적 중 운송장의 웹훅 TTL 연장 + 상태 폴링. 운송장 단위로 ① 웹훅 재등록(만료 연장, 초기 등록 실패 자가 치유) ② 최신 상태 동기화(콜백 유실 안전망)를
 * 수행한다. 재등록이 실패해도 폴링은 진행한다 — 둘은 독립적인 안전망이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingWebhookRefreshService {

  // 한 주기에 처리할 운송장 수 상한. 초과분은 다음 주기에 처리한다.
  private static final int BATCH_SIZE = 500;

  private final DeliveryTrackerClient deliveryTrackerClient;
  private final DeliveryDomainService deliveryDomainService;
  private final TrackingSyncService trackingSyncService;

  /**
   * 추적 중(SHIPPING·DELIVERED) 운송장을 상태 변경이 오래된 순으로 최대 {@link #BATCH_SIZE} 개 조회한다. 상한을 넘는 초과분은 이번 주기에
   * 빠지지만 정체된 운송장이 우선이라 다음 주기들에서 따라잡힌다 — 상한 도달이 반복되면 BATCH_SIZE 상향이 필요하다.
   */
  public List<TrackedParcel> findRefreshTargets() {
    List<TrackedParcel> targets = deliveryDomainService.findTrackedParcels(BATCH_SIZE);
    if (targets.size() >= BATCH_SIZE) {
      log.warn("추적 웹훅 갱신 - 대상이 배치 상한({})에 도달 — 반복되면 상한 조정 필요", BATCH_SIZE);
    }
    return targets;
  }

  /**
   * 운송장 1건의 웹훅 연장 + 폴링. 재등록 실패는 삼키고 폴링은 시도하며(둘은 독립 안전망), 폴링 실패는 호출자에게 전파해 건별 격리·집계를 맡긴다.
   *
   * @return 웹훅 재등록(TTL 연장) 성공 여부 — 실패 건수를 집계에 정확히 남기기 위한 값
   */
  public boolean refresh(final TrackedParcel parcel, final Instant expirationTime) {
    String carrierId = DeliveryTrackerCarriers.carrierId(parcel.shippingMethod());
    boolean renewed = true;
    try {
      deliveryTrackerClient.registerWebhook(carrierId, parcel.trackingNumber(), expirationTime);
    } catch (DeliveryTrackerException e) {
      renewed = false;
      log.error(
          "추적 웹훅 재등록 실패 - trackingNumber={} (다음 주기에 재시도, 폴링은 계속)", parcel.trackingNumber(), e);
    }
    trackingSyncService.sync(carrierId, parcel.trackingNumber());
    return renewed;
  }
}
