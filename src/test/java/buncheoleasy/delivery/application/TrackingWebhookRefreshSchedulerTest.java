package buncheoleasy.delivery.application;

import static org.mockito.BDDMockito.then;

import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerProperties;
import buncheoleasy.global.scheduler.SchedulerActivationGate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingWebhookRefreshScheduler 단위 테스트")
class TrackingWebhookRefreshSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-08-04T03:00:00Z");

  @InjectMocks private TrackingWebhookRefreshScheduler trackingWebhookRefreshScheduler;

  @Mock private TrackingWebhookRefreshService trackingWebhookRefreshService;

  @Mock private DeliveryTrackerClient deliveryTrackerClient;

  @Mock private DeliveryTrackerProperties deliveryTrackerProperties;

  @Mock private SchedulerActivationGate schedulerActivationGate;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void 기동_유예_중에는_웹훅_갱신을_시도하지_않는다() {
    // 12h fixedDelay 라 첫 발화가 skip 되면 다음 기회가 12시간 뒤다 — 그래서 initial-delay
    // (6분) > 유예(300s) 계약이 존재한다(application.yaml·게이트 javadoc). 게이트 차단
    // 자체는 여기서 고정한다.
    BDDMockito.given(schedulerActivationGate.isActive()).willReturn(false);

    trackingWebhookRefreshScheduler.refreshTrackedParcels();

    then(trackingWebhookRefreshService).shouldHaveNoInteractions();
    then(deliveryTrackerClient).shouldHaveNoInteractions();
  }
}
