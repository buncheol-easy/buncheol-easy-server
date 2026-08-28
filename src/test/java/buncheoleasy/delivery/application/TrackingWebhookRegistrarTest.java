package buncheoleasy.delivery.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerException;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerProperties;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingWebhookRegistrar 단위 테스트")
class TrackingWebhookRegistrarTest {

  private static final Instant NOW = Instant.parse("2026-03-23T12:00:00Z");
  private static final Long DELIVERY_ID = 10L;
  private static final Duration TTL = Duration.ofHours(48);

  @Mock private DeliveryTrackerClient deliveryTrackerClient;
  @Mock private DeliveryDomainService deliveryDomainService;

  private TrackingWebhookRegistrar registrar;

  @BeforeEach
  void setUp() {
    DeliveryTrackerProperties properties =
        new DeliveryTrackerProperties(
            "https://api",
            "id",
            "secret",
            "http://cb",
            "token",
            TTL,
            Duration.ofSeconds(3),
            Duration.ofSeconds(5),
            Duration.ofMillis(200));
    registrar =
        new TrackingWebhookRegistrar(
            deliveryTrackerClient,
            properties,
            deliveryDomainService,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private Delivery createShippingDelivery(
      final ShippingMethod method, final String trackingNumber) {
    Delivery delivery = Delivery.createSnapshot(20L, null, method, "GS25 강남점", "테스트유저", "01012345678");
    setField(delivery, "trackingNumber", trackingNumber);
    return delivery;
  }

  @Test
  void 운송장_등록_이벤트를_받아_TTL_만큼의_만료시각으로_웹훅을_등록한다() {
    // given
    given(deliveryTrackerClient.isEnabled()).willReturn(true);
    given(deliveryDomainService.getDelivery(DELIVERY_ID))
        .willReturn(createShippingDelivery(ShippingMethod.GS25_HALF, "TRACK123"));

    // when
    registrar.onTrackingRegistered(new TrackingRegisteredEvent(DELIVERY_ID));

    // then
    then(deliveryTrackerClient).should().registerWebhook("kr.cvsnet", "TRACK123", NOW.plus(TTL));
  }

  @Test
  void CU_반값택배는_cupost_캐리어로_등록한다() {
    // given
    given(deliveryTrackerClient.isEnabled()).willReturn(true);
    given(deliveryDomainService.getDelivery(DELIVERY_ID))
        .willReturn(createShippingDelivery(ShippingMethod.CU_HALF, "TRACK123"));

    // when
    registrar.onTrackingRegistered(new TrackingRegisteredEvent(DELIVERY_ID));

    // then
    then(deliveryTrackerClient).should().registerWebhook("kr.cupost", "TRACK123", NOW.plus(TTL));
  }

  @Test
  void 클라이언트가_미설정이면_등록을_건너뛴다() {
    // given
    given(deliveryTrackerClient.isEnabled()).willReturn(false);

    // when
    registrar.onTrackingRegistered(new TrackingRegisteredEvent(DELIVERY_ID));

    // then
    then(deliveryDomainService).shouldHaveNoInteractions();
  }

  @Test
  void 등록_실패는_삼키고_전파하지_않는다() {
    // 갱신 스케줄러가 재등록으로 자가 치유하므로 리스너는 로깅만 한다.
    given(deliveryTrackerClient.isEnabled()).willReturn(true);
    given(deliveryDomainService.getDelivery(DELIVERY_ID))
        .willReturn(createShippingDelivery(ShippingMethod.GS25_HALF, "TRACK123"));
    willThrow(new DeliveryTrackerException("통신 오류"))
        .given(deliveryTrackerClient)
        .registerWebhook("kr.cvsnet", "TRACK123", NOW.plus(TTL));

    // when & then
    assertThatCode(() -> registrar.onTrackingRegistered(new TrackingRegisteredEvent(DELIVERY_ID)))
        .doesNotThrowAnyException();
  }

  @Test
  void 배송_조회_실패도_삼키고_전파하지_않는다() {
    // given
    given(deliveryTrackerClient.isEnabled()).willReturn(true);
    given(deliveryDomainService.getDelivery(DELIVERY_ID))
        .willThrow(new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));

    // when & then
    assertThatCode(() -> registrar.onTrackingRegistered(new TrackingRegisteredEvent(DELIVERY_ID)))
        .doesNotThrowAnyException();
    then(deliveryTrackerClient).should(never()).registerWebhook(anyString(), anyString(), any());
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
