package buncheoleasy.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Delivery 도메인 단위 테스트")
class DeliveryTest {

  private static final Long PARTICIPATION_ID = 1L;
  private static final ShippingMethod SHIPPING_METHOD = ShippingMethod.GS25_HALF;
  private static final String STORE_NAME = "GS25 강남점";
  private static final String RECEIVER_NICKNAME = "테스트유저";
  private static final String RECEIVER_PHONE = "01012345678";
  private static final Instant NOW = Instant.parse("2026-03-23T12:00:00Z");

  @Nested
  @DisplayName("createSnapshot 테스트")
  class CreateSnapshotTest {

    @Test
    void 배송_스냅샷이_정상_생성된다() {
      Delivery delivery =
          Delivery.createSnapshot(
              PARTICIPATION_ID, SHIPPING_METHOD, STORE_NAME, RECEIVER_NICKNAME, RECEIVER_PHONE);

      assertThat(delivery.getParticipationId()).isEqualTo(PARTICIPATION_ID);
      assertThat(delivery.getShippingMethod()).isEqualTo(SHIPPING_METHOD);
      assertThat(delivery.getStoreName()).isEqualTo(STORE_NAME);
      assertThat(delivery.getReceiverNickname()).isEqualTo(RECEIVER_NICKNAME);
      assertThat(delivery.getReceiverPhoneNumber()).isEqualTo(RECEIVER_PHONE);
      assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SNAPSHOTTED);
      assertThat(delivery.getTrackingNumber()).isNull();
    }

    @Test
    void 배송방법이_null이면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  Delivery.createSnapshot(
                      PARTICIPATION_ID, null, STORE_NAME, RECEIVER_NICKNAME, RECEIVER_PHONE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_SHIPPING_METHOD_REQUIRED);
    }

    @Test
    void 지점명이_비어있으면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  Delivery.createSnapshot(
                      PARTICIPATION_ID, SHIPPING_METHOD, "", RECEIVER_NICKNAME, RECEIVER_PHONE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_STORE_NAME_REQUIRED);
    }

    @Test
    void 수령인_닉네임이_null이면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  Delivery.createSnapshot(
                      PARTICIPATION_ID, SHIPPING_METHOD, STORE_NAME, null, RECEIVER_PHONE))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_RECEIVER_NICKNAME_REQUIRED);
    }

    @Test
    void 수령인_연락처가_null이면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  Delivery.createSnapshot(
                      PARTICIPATION_ID, SHIPPING_METHOD, STORE_NAME, RECEIVER_NICKNAME, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_RECEIVER_PHONE_REQUIRED);
    }
  }

  @Nested
  @DisplayName("registerTracking 테스트")
  class RegisterTrackingTest {

    @Test
    void SNAPSHOTTED_상태에서_운송장_등록시_SHIPPING으로_전이된다() {
      Delivery delivery = createSnapshotDelivery();

      delivery.registerTracking("TRACK123", NOW);

      assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
      assertThat(delivery.getTrackingNumber()).isEqualTo("TRACK123");
      assertThat(delivery.getTrackingRegisteredAt()).isEqualTo(NOW);
    }

    @Test
    void SHIPPING_상태에서_운송장_등록시_운송장_번호만_업데이트된다() {
      Delivery delivery = createSnapshotDelivery();
      delivery.registerTracking("TRACK123", NOW);

      Instant later = NOW.plus(1, ChronoUnit.HOURS);
      delivery.registerTracking("TRACK456", later);

      assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
      assertThat(delivery.getTrackingNumber()).isEqualTo("TRACK456");
      assertThat(delivery.getTrackingRegisteredAt()).isEqualTo(later);
    }

    @Test
    void 운송장_번호가_null이면_예외가_발생한다() {
      Delivery delivery = createSnapshotDelivery();

      assertThatThrownBy(() -> delivery.registerTracking(null, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_TRACKING_NUMBER_REQUIRED);
    }

    @Test
    void 운송장_번호가_빈_문자열이면_예외가_발생한다() {
      Delivery delivery = createSnapshotDelivery();

      assertThatThrownBy(() -> delivery.registerTracking("  ", NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_TRACKING_NUMBER_REQUIRED);
    }

    @Test
    void RECEIVED_상태에서_운송장_등록시_예외가_발생한다() {
      Delivery delivery = createSnapshotDelivery();
      delivery.registerTracking("TRACK123", NOW);
      delivery.confirmReceipt(NOW.plus(1, ChronoUnit.DAYS));

      assertThatThrownBy(() -> delivery.registerTracking("TRACK999", NOW.plus(2, ChronoUnit.DAYS)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("confirmReceipt 테스트")
  class ConfirmReceiptTest {

    @Test
    void SHIPPING_상태에서_수령_완료시_RECEIVED로_전이된다() {
      Delivery delivery = createSnapshotDelivery();
      delivery.registerTracking("TRACK123", NOW);

      delivery.confirmReceipt(NOW.plus(1, ChronoUnit.DAYS));

      assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
      assertThat(delivery.getReceivedAt()).isEqualTo(NOW.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void DELIVERED_상태에서_수령_완료시_RECEIVED로_전이된다() {
      Delivery delivery = createSnapshotDelivery();
      delivery.registerTracking("TRACK123", NOW);
      setField(delivery, "status", DeliveryStatus.DELIVERED);

      delivery.confirmReceipt(NOW.plus(1, ChronoUnit.DAYS));

      assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RECEIVED);
    }

    @Test
    void SNAPSHOTTED_상태에서_수령_완료시_예외가_발생한다() {
      Delivery delivery = createSnapshotDelivery();

      assertThatThrownBy(() -> delivery.confirmReceipt(NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }

    @Test
    void RECEIVED_상태에서_수령_완료시_예외가_발생한다() {
      Delivery delivery = createSnapshotDelivery();
      delivery.registerTracking("TRACK123", NOW);
      delivery.confirmReceipt(NOW.plus(1, ChronoUnit.DAYS));

      assertThatThrownBy(() -> delivery.confirmReceipt(NOW.plus(2, ChronoUnit.DAYS)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.DELIVERY_STATE_TRANSITION_INVALID);
    }
  }

  private Delivery createSnapshotDelivery() {
    return Delivery.createSnapshot(
        PARTICIPATION_ID, SHIPPING_METHOD, STORE_NAME, RECEIVER_NICKNAME, RECEIVER_PHONE);
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
