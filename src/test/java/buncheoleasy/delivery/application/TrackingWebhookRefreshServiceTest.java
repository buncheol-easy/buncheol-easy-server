package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.TrackedParcel;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerException;
import buncheoleasy.user.domain.shipping.ShippingMethod;
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
    assertThat(trackingWebhookRefreshService.refresh(PARCEL, EXPIRATION)).isTrue();
    then(deliveryTrackerClient).should().registerWebhook("kr.cvsnet", "TRACK123", EXPIRATION);
    then(trackingSyncService).should().sync("kr.cvsnet", "TRACK123");
  }

  @Test
  void 웹훅_재등록이_실패해도_폴링은_진행하고_실패를_돌려준다() {
    // 재등록과 폴링은 독립적인 안전망 — 한쪽 실패가 다른 쪽을 막으면 안 된다.
    willThrow(new DeliveryTrackerException("통신 오류"))
        .given(deliveryTrackerClient)
        .registerWebhook("kr.cvsnet", "TRACK123", EXPIRATION);

    // when & then
    assertThat(trackingWebhookRefreshService.refresh(PARCEL, EXPIRATION)).isFalse();
    then(trackingSyncService).should().sync("kr.cvsnet", "TRACK123");
  }

  @Test
  void 폴링_실패는_호출자에게_전파해_건별_격리를_맡긴다() {
    // given
    willThrow(new DeliveryTrackerException("통신 오류"))
        .given(trackingSyncService)
        .sync("kr.cvsnet", "TRACK123");

    // when & then
    assertThatThrownBy(() -> trackingWebhookRefreshService.refresh(PARCEL, EXPIRATION))
        .isInstanceOf(DeliveryTrackerException.class);
  }

  @Test
  void 갱신_대상은_추적_중_운송장을_상한까지_조회한다() {
    // given
    given(deliveryDomainService.findTrackedParcels(500)).willReturn(List.of(PARCEL));

    // when & then
    assertThat(trackingWebhookRefreshService.findRefreshTargets()).containsExactly(PARCEL);
  }
}
