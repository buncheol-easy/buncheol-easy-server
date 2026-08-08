package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;

import buncheoleasy.delivery.application.TrackingWebhookRefreshService.RefreshOutcome;
import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerProperties;
import buncheoleasy.global.scheduler.SchedulerActivationGate;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingWebhookRefreshScheduler 단위 테스트")
class TrackingWebhookRefreshSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-08-04T03:00:00Z");
  private static final Duration TTL = Duration.ofHours(48);
  private static final Instant EXPIRATION = NOW.plus(TTL);
  private static final TrackedParcel PARCEL_A = new TrackedParcel(ShippingMethod.GS25_HALF, "A");
  private static final TrackedParcel PARCEL_B = new TrackedParcel(ShippingMethod.GS25_HALF, "B");
  private static final TrackedParcel PARCEL_C = new TrackedParcel(ShippingMethod.GS25_HALF, "C");

  @InjectMocks private TrackingWebhookRefreshScheduler trackingWebhookRefreshScheduler;

  @Mock private TrackingWebhookRefreshService trackingWebhookRefreshService;

  @Mock private DeliveryTrackerClient deliveryTrackerClient;

  @Mock private DeliveryTrackerProperties deliveryTrackerProperties;

  @Mock private SchedulerActivationGate schedulerActivationGate;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void attachLogAppender() {
    logAppender = new ListAppender<>();
    logAppender.start();
    schedulerLogger().addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    schedulerLogger().detachAppender(logAppender);
  }

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

  @Test
  void 요약은_연장_실패와_폴링_실패를_각각_집계한다() {
    // 이중 실패 건이 "폴링 실패" 로만 잡혀 연장실패: 0 으로 왜곡되던 장애(2026-08-07)의 회귀 방지선.
    givenActiveSchedulerWith(PARCEL_A, PARCEL_B, PARCEL_C);
    given(trackingWebhookRefreshService.refresh(PARCEL_A, EXPIRATION))
        .willReturn(new RefreshOutcome(true, true));
    given(trackingWebhookRefreshService.refresh(PARCEL_B, EXPIRATION))
        .willReturn(new RefreshOutcome(false, true));
    given(trackingWebhookRefreshService.refresh(PARCEL_C, EXPIRATION))
        .willReturn(new RefreshOutcome(false, false));

    trackingWebhookRefreshScheduler.refreshTrackedParcels();

    assertThat(logMessages()).contains("추적 웹훅 갱신 완료 - 대상: 3, 연장: 1, 연장실패: 2, 폴링실패: 1");
  }

  @Test
  void 예상_밖_예외는_해당_건만_이중_실패로_집계하고_나머지는_계속_처리한다() {
    givenActiveSchedulerWith(PARCEL_A, PARCEL_B);
    given(trackingWebhookRefreshService.refresh(PARCEL_A, EXPIRATION))
        .willThrow(new IllegalStateException("예상 밖 실패"));
    given(trackingWebhookRefreshService.refresh(PARCEL_B, EXPIRATION))
        .willReturn(new RefreshOutcome(true, true));

    trackingWebhookRefreshScheduler.refreshTrackedParcels();

    assertThat(logMessages()).contains("추적 웹훅 갱신 완료 - 대상: 2, 연장: 1, 연장실패: 1, 폴링실패: 1");
  }

  @Test
  void 인터럽트가_서면_남은_건을_건너뛰고_배치를_중단한다() {
    // 종료 시 shutdownNow 인터럽트가 배치를 관통하면 남은 건 전체가 오탐 ERROR 로 쏟아진다 — 중단이 정답.
    givenActiveSchedulerWith(PARCEL_A, PARCEL_B);
    willAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              return new RefreshOutcome(false, false);
            })
        .given(trackingWebhookRefreshService)
        .refresh(PARCEL_A, EXPIRATION);

    try {
      trackingWebhookRefreshScheduler.refreshTrackedParcels();
    } finally {
      // 테스트 스레드의 인터럽트 플래그를 반드시 청소한다 — 남기면 이후 테스트가 오염된다.
      Thread.interrupted();
    }

    then(trackingWebhookRefreshService).should(times(1)).refresh(any(), any());
    assertThat(logMessages()).contains("추적 웹훅 갱신 중단(인터럽트) - 처리 1/2");
  }

  private void givenActiveSchedulerWith(final TrackedParcel... parcels) {
    given(schedulerActivationGate.isActive()).willReturn(true);
    given(deliveryTrackerClient.isEnabled()).willReturn(true);
    given(deliveryTrackerProperties.webhookTtl()).willReturn(TTL);
    given(trackingWebhookRefreshService.findRefreshTargets(NOW)).willReturn(List.of(parcels));
  }

  private Logger schedulerLogger() {
    return (Logger) LoggerFactory.getLogger(TrackingWebhookRefreshScheduler.class);
  }

  private List<String> logMessages() {
    return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
