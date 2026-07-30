package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerException;
import buncheoleasy.delivery.infrastructure.TrackLastEvent;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingSyncService 단위 테스트")
class TrackingSyncServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-23T12:00:00Z");
  private static final Instant EVENT_TIME = Instant.parse("2026-03-23T10:00:00Z");
  private static final String CARRIER_GS25 = "kr.cvsnet";
  private static final String TRACKING_NUMBER = "TRACK123";
  private static final Set<DeliveryStatus> TRACKED =
      Set.of(DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED);

  @InjectMocks private TrackingSyncService trackingSyncService;

  @Mock private DeliveryTrackerClient deliveryTrackerClient;
  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private TrackingTransitionService trackingTransitionService;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private Delivery deliveryWithId(final Long id) {
    Delivery delivery =
        Delivery.createSnapshot(id, ShippingMethod.GS25_HALF, "GS25 강남점", "테스트유저", "01012345678");
    setField(delivery, "id", id);
    return delivery;
  }

  private void givenTargets(final Delivery... deliveries) {
    given(deliveryTrackerClient.isEnabled()).willReturn(true);
    given(
            deliveryDomainService.findAllByTrackingNumber(
                TRACKING_NUMBER, ShippingMethod.GS25_HALF, TRACKED))
        .willReturn(List.of(deliveries));
  }

  @Nested
  @DisplayName("상태 매핑")
  class StatusMappingTest {

    @Test
    void AVAILABLE_FOR_PICKUP_은_지점_도착_전이를_적용한다() {
      // given
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("AVAILABLE_FOR_PICKUP", "점포도착", EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markDelivered(1L, EVENT_TIME, NOW);
      then(trackingTransitionService).should(never()).markReceived(anyLong(), any(), any());
    }

    @Test
    void DELIVERED_는_고객_수령_전이를_적용한다() {
      // given
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("DELIVERED", "배송완료", EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(1L, EVENT_TIME, NOW);
    }

    @Test
    void 이동중_같은_그_외_상태는_전이하지_않는다() {
      // given
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("IN_TRANSIT", "이동중", EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }

    @Test
    void 모르는_상태_코드는_전이하지_않는다() {
      // Delivery Tracker 가 코드를 추가해도 오동작하지 않아야 한다.
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("SOMETHING_NEW", "새상태", EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }

    @Test
    void UNKNOWN_코드라도_원문이_점포도착이면_지점_도착_전이를_적용한다() {
      // cupost 는 코드 정규화가 안 돼 UNKNOWN + 원문 문구로 온다 (2026-07 실측).
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("UNKNOWN", "점포도착", EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markDelivered(1L, EVENT_TIME, NOW);
    }

    @Test
    void UNKNOWN_코드라도_원문이_수령완료면_고객_수령_전이를_적용한다() {
      // given
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("UNKNOWN", "수령완료", EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(1L, EVENT_TIME, NOW);
    }

    @Test
    void UNKNOWN_코드에_모르는_원문이면_전이하지_않는다() {
      // 문구가 바뀌면 오탐 대신 스킵(fail-safe) — 스킵 로그의 statusName 으로 발견해 매핑을 추가한다.
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("UNKNOWN", "이동중", EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }

    @Test
    void UNKNOWN_코드에_원문이_없어도_전이하지_않는다() {
      // given
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("UNKNOWN", null, EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }

    @Test
    void 이벤트_시각이_없으면_현재_시각으로_대체한다() {
      // given
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("DELIVERED", "배송완료", null)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(1L, NOW, NOW);
    }
  }

  @Nested
  @DisplayName("동기화 대상·환경 가드")
  class GuardTest {

    @Test
    void 미지원_캐리어_콜백은_무시한다() {
      // when
      trackingSyncService.sync("kr.cjlogistics", TRACKING_NUMBER);

      // then
      then(deliveryTrackerClient).shouldHaveNoInteractions();
      then(deliveryDomainService).shouldHaveNoInteractions();
    }

    @Test
    void 클라이언트_미설정이면_조회하지_않는다() {
      // given
      given(deliveryTrackerClient.isEnabled()).willReturn(false);

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(deliveryTrackerClient).should(never()).findLastEvent(anyString(), anyString());
    }

    @Test
    void 추적_중_배송이_없으면_조회_비용을_아끼고_끝낸다() {
      // given
      given(deliveryTrackerClient.isEnabled()).willReturn(true);
      given(
              deliveryDomainService.findAllByTrackingNumber(
                  TRACKING_NUMBER, ShippingMethod.GS25_HALF, TRACKED))
          .willReturn(List.of());

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(deliveryTrackerClient).should(never()).findLastEvent(anyString(), anyString());
    }

    @Test
    void 추적_정보가_없으면_전이하지_않는다() {
      // given
      givenTargets(deliveryWithId(1L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.empty());

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("한 운송장 다건 배송")
  class MultiDeliveryTest {

    @Test
    void 같은_운송장의_배송_전부에_전이를_적용한다() {
      // 관리자 벌크 등록은 한 운송장을 여러 배송에 매핑한다.
      givenTargets(deliveryWithId(1L), deliveryWithId(2L), deliveryWithId(3L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("DELIVERED", "배송완료", EVENT_TIME)));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(1L, EVENT_TIME, NOW);
      then(trackingTransitionService).should().markReceived(2L, EVENT_TIME, NOW);
      then(trackingTransitionService).should().markReceived(3L, EVENT_TIME, NOW);
    }

    @Test
    void 한_건_전이_실패가_나머지_전이를_막지_않는다() {
      // given
      givenTargets(deliveryWithId(1L), deliveryWithId(2L));
      given(deliveryTrackerClient.findLastEvent(CARRIER_GS25, TRACKING_NUMBER))
          .willReturn(Optional.of(new TrackLastEvent("DELIVERED", "배송완료", EVENT_TIME)));
      willThrow(new RuntimeException("DB 오류"))
          .given(trackingTransitionService)
          .markReceived(1L, EVENT_TIME, NOW);

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(2L, EVENT_TIME, NOW);
    }
  }

  @Nested
  @DisplayName("syncAsync — 콜백 수신 경로")
  class SyncAsyncTest {

    @Test
    void 동기화_실패를_삼키고_전파하지_않는다() {
      // 202 응답 후라 예외를 전파할 곳이 없다 — 갱신 스케줄러 폴링이 안전망.
      givenTargets(deliveryWithId(1L));
      willThrow(new DeliveryTrackerException("통신 오류"))
          .given(deliveryTrackerClient)
          .findLastEvent(CARRIER_GS25, TRACKING_NUMBER);

      // when & then
      assertThatCode(() -> trackingSyncService.syncAsync(CARRIER_GS25, TRACKING_NUMBER))
          .doesNotThrowAnyException();
    }
  }

  private void setField(final Object target, final String fieldName, final Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
