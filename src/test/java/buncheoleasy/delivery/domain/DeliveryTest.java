package buncheoleasy.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 상태 전이(SHIPPING·DELIVERED·RECEIVED)는 리포지토리 CAS UPDATE 로만 수행하므로
// 전이 검증은 JpaDeliveryRepositoryAdapterTest 에 있다. 여기선 스냅샷 생성 검증만 다룬다.
@DisplayName("Delivery 도메인 단위 테스트")
class DeliveryTest {

  private static final Long PARTICIPATION_ID = 1L;
  private static final ShippingMethod SHIPPING_METHOD = ShippingMethod.GS25_HALF;
  private static final String STORE_NAME = "GS25 강남점";
  private static final String RECEIVER_NICKNAME = "테스트유저";
  private static final String RECEIVER_PHONE = "01012345678";

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

}
