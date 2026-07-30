package buncheoleasy.delivery.application;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerCarriers;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerProperties;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 운송장 등록 커밋 후 Delivery Tracker 에 추적 웹훅을 등록한다. 재등록은 만료 연장으로 동작하는 멱등 연산이라 이벤트 중복이 무해하고, 등록 실패는
 * 로깅만 한다 — 갱신 스케줄러가 주기 재등록으로 자가 치유한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingWebhookRegistrar {

  private final DeliveryTrackerClient deliveryTrackerClient;
  private final DeliveryTrackerProperties deliveryTrackerProperties;
  private final DeliveryDomainService deliveryDomainService;
  private final Clock clock;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onTrackingRegistered(final TrackingRegisteredEvent event) {
    if (!deliveryTrackerClient.isEnabled()) {
      log.debug("Delivery Tracker 미설정 - 웹훅 등록 건너뜀 - deliveryId={}", event.deliveryId());
      return;
    }
    try {
      Delivery delivery = deliveryDomainService.getDelivery(event.deliveryId());
      String carrierId = DeliveryTrackerCarriers.carrierId(delivery.getShippingMethod());
      Instant expirationTime = Instant.now(clock).plus(deliveryTrackerProperties.webhookTtl());
      deliveryTrackerClient.registerWebhook(
          carrierId, delivery.getTrackingNumber(), expirationTime);
      log.info(
          "배송 추적 웹훅 등록 - deliveryId={} carrierId={} trackingNumber={}",
          event.deliveryId(),
          carrierId,
          delivery.getTrackingNumber());
    } catch (RuntimeException e) {
      log.error("배송 추적 웹훅 등록 실패 - deliveryId={} (갱신 스케줄러가 재시도)", event.deliveryId(), e);
    }
  }
}
