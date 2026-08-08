package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import buncheoleasy.delivery.application.TrackingWebhookRefreshService.RefreshOutcome;

import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerException;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingWebhookRefreshService 단위 테스트")
class TrackingWebhookRefreshServiceTest {

  private static final Instant EXPIRATION = Instant.parse("2026-03-25T12:00:00Z");
  private static final TrackedParcel PARCEL =
      new TrackedParcel(ShippingMethod.GS25_HALF, "TRACK123");

  @InjectMocks private TrackingWebhookRefreshService trackingWebhookRefreshService;

  @Mock private DeliveryTrackerClient deliveryTrackerClient;
  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private TrackingSyncService trackingSyncService;

  @Test
  void 운송장의_웹훅을_연장하고_최신_상태를_폴링한다() {
    // when & then
    assertThat(trackingWebhookRefreshService.refresh(PARCEL, EXPIRATION))
        .isEqualTo(new RefreshOutcome(true, true));
    then(deliveryTrackerClient).should().registerWebhook("kr.cvsnet", "TRACK123", EXPIRATION);
    then(trackingSyncService).should().sync("kr.cvsnet", "TRACK123");
  }

  @Test
  void 웹훅_재등록이_실패해도_폴링은_진행하고_연장_실패를_돌려준다() {
    // 재등록과 폴링은 독립적인 안전망 — 한쪽 실패가 다른 쪽을 막으면 안 된다.
    willThrow(new DeliveryTrackerException("통신 오류"))
        .given(deliveryTrackerClient)
        .registerWebhook("kr.cvsnet", "TRACK123", EXPIRATION);

    // when & then
    assertThat(trackingWebhookRefreshService.refresh(PARCEL, EXPIRATION))
        .isEqualTo(new RefreshOutcome(false, true));
    then(trackingSyncService).should().sync("kr.cvsnet", "TRACK123");
  }

  @Test
  void 폴링이_실패해도_예외_대신_폴링_실패를_돌려준다() {
    // 웹훅·폴링이 모두 실패한 건이 "폴링 실패" 로만 집계되던 왜곡을 막기 위해 예외 전파 대신 플래그로 돌려준다.
    willThrow(new DeliveryTrackerException("통신 오류"))
        .given(trackingSyncService)
        .sync("kr.cvsnet", "TRACK123");

    // when & then
    assertThat(trackingWebhookRefreshService.refresh(PARCEL, EXPIRATION))
        .isEqualTo(new RefreshOutcome(true, false));
  }

  @Test
  void 웹훅_재등록과_폴링이_모두_실패하면_둘_다_실패로_돌려준다() {
    // given
    willThrow(new DeliveryTrackerException("rate limit"))
        .given(deliveryTrackerClient)
        .registerWebhook("kr.cvsnet", "TRACK123", EXPIRATION);
    willThrow(new DeliveryTrackerException("rate limit"))
        .given(trackingSyncService)
        .sync("kr.cvsnet", "TRACK123");

    // when & then
    assertThat(trackingWebhookRefreshService.refresh(PARCEL, EXPIRATION))
        .isEqualTo(new RefreshOutcome(false, false));
  }

  @Test
  void 갱신_대상은_등록_30일_이내_추적_중_운송장을_상한까지_조회한다() {
    // given
    Instant now = Instant.parse("2026-03-23T12:00:00Z");
    given(deliveryDomainService.findTrackedParcels(now.minus(Duration.ofDays(30)), 500))
        .willReturn(List.of(PARCEL));

    // when & then
    assertThat(trackingWebhookRefreshService.findRefreshTargets(now)).containsExactly(PARCEL);
  }
}
