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
import buncheoleasy.delivery.infrastructure.TrackEvent;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
  private static final String CARRIER_CU = "kr.cupost";
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
        Delivery.createSnapshot(id, null, ShippingMethod.GS25_HALF, "GS25 강남점", "테스트유저", "01012345678");
    setField(delivery, "id", id);
    return delivery;
  }

  private void givenTargets(final Delivery... deliveries) {
    givenTargets(ShippingMethod.GS25_HALF, deliveries);
  }

  private void givenTargets(final ShippingMethod shippingMethod, final Delivery... deliveries) {
    given(deliveryTrackerClient.isEnabled()).willReturn(true);
    given(deliveryDomainService.findAllByTrackingNumber(TRACKING_NUMBER, shippingMethod, TRACKED))
        .willReturn(List.of(deliveries));
  }

  private void givenEvents(final String carrierId, final TrackEvent... events) {
    given(deliveryTrackerClient.findRecentEvents(carrierId, TRACKING_NUMBER))
        .willReturn(List.of(events));
  }

  private static TrackEvent event(final String code, final String name, final Instant time) {
    return new TrackEvent(code, name, time);
  }

  private static Instant kst(final String localDateTime) {
    return OffsetDateTime.parse(localDateTime + "+09:00").toInstant();
  }

  @Nested
  @DisplayName("GS25 상태 판정")
  class Gs25StatusTest {

    @Test
    void 점포_도착_AVAILABLE_FOR_PICKUP_이면_지점_도착_전이를_적용한다() {
      // given
      givenTargets(deliveryWithId(1L));
      givenEvents(
          CARRIER_GS25,
          event("OUT_FOR_DELIVERY", "배송기사 인계", kst("2026-10-04T19:30:48")),
          event("AVAILABLE_FOR_PICKUP", "점포에 도착", EVENT_TIME));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markDelivered(1L, EVENT_TIME, NOW);
      then(trackingTransitionService).should(never()).markReceived(anyLong(), any(), any());
    }

    @Test
    void 고객_전달_DELIVERED_가_있으면_고객_수령_전이를_적용한다() {
      // given
      givenTargets(deliveryWithId(1L));
      givenEvents(
          CARRIER_GS25,
          event("AVAILABLE_FOR_PICKUP", "점포에 도착", kst("2026-10-04T21:31:33")),
          event("DELIVERED", "고객에게 전달", EVENT_TIME));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(1L, EVENT_TIME, NOW);
      then(trackingTransitionService).should(never()).markDelivered(anyLong(), any(), any());
    }

    @Test
    void 이동중_같은_그_외_상태만_있으면_전이하지_않는다() {
      // given
      givenTargets(deliveryWithId(1L));
      givenEvents(CARRIER_GS25, event("IN_TRANSIT", "이동중", EVENT_TIME));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }

    @Test
    void 모르는_상태_코드는_전이하지_않는다() {
      // Delivery Tracker 가 코드를 추가해도 오동작하지 않아야 한다.
      givenTargets(deliveryWithId(1L));
      givenEvents(CARRIER_GS25, event("SOMETHING_NEW", "새상태", EVENT_TIME));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }

    @Test
    void 이벤트_시각이_없으면_현재_시각으로_대체한다() {
      // given
      givenTargets(deliveryWithId(1L));
      givenEvents(CARRIER_GS25, event("DELIVERED", "고객에게 전달", null));

      // when
      trackingSyncService.sync(CARRIER_GS25, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(1L, NOW, NOW);
    }
  }

  /** 기록은 2026-10 운영 CU 운송장의 실제 상태 코드·원문·순서를 옮긴 것이다. */
  @Nested
  @DisplayName("CU 상태 판정")
  class CuStatusTest {

    private static final Instant STORE_ARRIVED = kst("2026-10-23T14:17:00");
    private static final Instant DELIVERY_DONE = kst("2026-10-23T14:17:18");
    private static final Instant PICKED_UP = kst("2026-10-23T20:41:00");

    private TrackEvent[] untilOutForDelivery() {
      return new TrackEvent[] {
        event("UNKNOWN", "접수완료", kst("2026-10-21T17:08:00")),
        event("AT_PICKUP", "집하완료", kst("2026-10-21T19:09:38")),
        event("IN_TRANSIT", "도착", kst("2026-10-22T23:16:21")),
        event("IN_TRANSIT", "발송", kst("2026-10-22T23:19:40")),
        event("OUT_FOR_DELIVERY", "배달전", kst("2026-10-23T07:38:01"))
      };
    }

    private void givenCuEvents(final TrackEvent... tail) {
      List<TrackEvent> events = new ArrayList<>(List.of(untilOutForDelivery()));
      events.addAll(List.of(tail));
      givenEvents(CARRIER_CU, events.toArray(TrackEvent[]::new));
    }

    @Test
    void 배달완료_DELIVERED_는_고객_수령이_아니라_지점_도착이다() {
      // given
      givenTargets(ShippingMethod.CU_HALF, deliveryWithId(1L));
      givenCuEvents(
          event("UNKNOWN", "점포도착", STORE_ARRIVED),
          event("DELIVERED", "배달완료", DELIVERY_DONE));

      // when
      trackingSyncService.sync(CARRIER_CU, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markDelivered(1L, STORE_ARRIVED, NOW);
      then(trackingTransitionService).should(never()).markReceived(anyLong(), any(), any());
    }

    @Test
    void 배달완료가_점포도착보다_먼저_와도_먼저_온_시각으로_지점_도착을_적용한다() {
      // given
      givenTargets(ShippingMethod.CU_HALF, deliveryWithId(1L));
      givenCuEvents(
          event("DELIVERED", "배달완료", DELIVERY_DONE),
          event("UNKNOWN", "점포도착", kst("2026-10-23T14:29:00")));

      // when
      trackingSyncService.sync(CARRIER_CU, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markDelivered(1L, DELIVERY_DONE, NOW);
      then(trackingTransitionService).should(never()).markReceived(anyLong(), any(), any());
    }

    @Test
    void 수령완료가_있으면_수령완료_시각으로_고객_수령_전이를_적용한다() {
      // given
      givenTargets(ShippingMethod.CU_HALF, deliveryWithId(1L));
      givenCuEvents(
          event("UNKNOWN", "점포도착", STORE_ARRIVED),
          event("DELIVERED", "배달완료", DELIVERY_DONE),
          event("UNKNOWN", "수령완료", PICKED_UP));

      // when
      trackingSyncService.sync(CARRIER_CU, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(1L, PICKED_UP, NOW);
      then(trackingTransitionService).should(never()).markDelivered(anyLong(), any(), any());
    }

    @Test
    void 점포_도착_전_기록만_있으면_전이하지_않는다() {
      // given
      givenTargets(ShippingMethod.CU_HALF, deliveryWithId(1L));
      givenCuEvents();

      // when
      trackingSyncService.sync(CARRIER_CU, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }

    @Test
    void 원문이_다른_DELIVERED_도_고객_수령으로_보지_않는다() {
      // given
      givenTargets(ShippingMethod.CU_HALF, deliveryWithId(1L));
      givenCuEvents(event("DELIVERED", "고객전달", DELIVERY_DONE));

      // when
      trackingSyncService.sync(CARRIER_CU, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markDelivered(1L, DELIVERY_DONE, NOW);
      then(trackingTransitionService).should(never()).markReceived(anyLong(), any(), any());
    }

    @Test
    void 코드나_원문이_비어_있는_이벤트는_전이하지_않는다() {
      // given
      givenTargets(ShippingMethod.CU_HALF, deliveryWithId(1L));
      givenCuEvents(event("UNKNOWN", null, STORE_ARRIVED), event(null, null, DELIVERY_DONE));

      // when
      trackingSyncService.sync(CARRIER_CU, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).shouldHaveNoInteractions();
    }

    @Test
    void 점포도착이_AVAILABLE_FOR_PICKUP_코드로_와도_지점_도착을_적용한다() {
      // given
      givenTargets(ShippingMethod.CU_HALF, deliveryWithId(1L));
      givenCuEvents(event("AVAILABLE_FOR_PICKUP", "점포도착", STORE_ARRIVED));

      // when
      trackingSyncService.sync(CARRIER_CU, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markDelivered(1L, STORE_ARRIVED, NOW);
    }

    @Test
    void 수령완료가_DELIVERED_코드로_와도_원문으로_고객_수령을_판정한다() {
      // given
      givenTargets(ShippingMethod.CU_HALF, deliveryWithId(1L));
      givenCuEvents(
          event("UNKNOWN", "점포도착", STORE_ARRIVED),
          event("DELIVERED", "배달완료", DELIVERY_DONE),
          event("DELIVERED", "수령완료", PICKED_UP));

      // when
      trackingSyncService.sync(CARRIER_CU, TRACKING_NUMBER);

      // then
      then(trackingTransitionService).should().markReceived(1L, PICKED_UP, NOW);
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
      then(deliveryTrackerClient).should(never()).findRecentEvents(anyString(), anyString());
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
      then(deliveryTrackerClient).should(never()).findRecentEvents(anyString(), anyString());
    }

    @Test
    void 추적_정보가_없으면_전이하지_않는다() {
      // given
      givenTargets(deliveryWithId(1L));
      givenEvents(CARRIER_GS25);

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
      givenEvents(CARRIER_GS25, event("DELIVERED", "고객에게 전달", EVENT_TIME));

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
      givenEvents(CARRIER_GS25, event("DELIVERED", "고객에게 전달", EVENT_TIME));
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
          .findRecentEvents(CARRIER_GS25, TRACKING_NUMBER);

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
