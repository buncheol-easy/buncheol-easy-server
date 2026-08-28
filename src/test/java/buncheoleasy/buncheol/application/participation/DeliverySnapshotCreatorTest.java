package buncheoleasy.buncheol.application.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.buncheol.application.DeliverySnapshotCreator;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.PhoneNumber;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliverySnapshotCreator 단위 테스트")
class DeliverySnapshotCreatorTest {

  @InjectMocks private DeliverySnapshotCreator deliverySnapshotCreator;

  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private ShippingAddressDomainService shippingAddressDomainService;
  @Mock private UserDomainService userDomainService;

  private static final Long PARTICIPATION_ID = 1L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;

  @Test
  void 참여_배송지_유저_정보를_그_시점_값으로_배송_스냅샷에_담는다() {
    Participation participation =
        Participation.create(
            1L,
            10L,
            PARTICIPANT_ID,
            SHIPPING_ADDRESS_ID,
            30_000L,
            0L,
            Instant.parse("2026-03-11T15:30:00Z"));
    setFieldValue(participation, "id", PARTICIPATION_ID);

    ShippingAddress shippingAddress =
        new ShippingAddress(
            SHIPPING_ADDRESS_ID, PARTICIPANT_ID, ShippingMethod.GS25_HALF, "GS25 강남점", null, false);
    given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID))
        .willReturn(shippingAddress);

    User user = User.create("KAKAO", "kakao123", "test@test.com");
    setFieldValue(user, "nickname", Nickname.of("TestUser"));
    setFieldValue(user, "phoneNumber", PhoneNumber.of("01012345678"));
    setFieldValue(user, "profileCompleted", true);
    given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(user);

    deliverySnapshotCreator.create(participation);

    ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);
    then(deliveryDomainService).should().createDelivery(deliveryCaptor.capture());

    Delivery delivery = deliveryCaptor.getValue();
    assertThat(delivery.getParticipationId()).isEqualTo(PARTICIPATION_ID);
    assertThat(delivery.getShippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
    assertThat(delivery.getStoreName()).isEqualTo("GS25 강남점");
    assertThat(delivery.getReceiverNickname()).isEqualTo("TestUser");
    assertThat(delivery.getReceiverPhoneNumber()).isEqualTo("01012345678");
  }

  @Test
  void 가입_미완료_유저면_스냅샷을_만들지_않고_예외가_발생한다() {
    Participation participation =
        Participation.create(
            1L,
            10L,
            PARTICIPANT_ID,
            SHIPPING_ADDRESS_ID,
            30_000L,
            0L,
            Instant.parse("2026-03-11T15:30:00Z"));
    setFieldValue(participation, "id", PARTICIPATION_ID);

    ShippingAddress shippingAddress =
        new ShippingAddress(
            SHIPPING_ADDRESS_ID, PARTICIPANT_ID, ShippingMethod.GS25_HALF, "GS25 강남점", null, false);
    given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID))
        .willReturn(shippingAddress);

    // User.create 직후 상태 = 가입 미완료(전화번호 없음). 가드 도입 전 생성된 Guest 참여가
    // 입금확인될 때 NPE(500) 대신 명시적 403 으로 실패하는지 확인한다.
    User user = User.create("KAKAO", "kakao123", "test@test.com");
    given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(user);

    assertThatThrownBy(() -> deliverySnapshotCreator.create(participation))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);

    then(deliveryDomainService).shouldHaveNoInteractions();
  }

  private void setFieldValue(final Object target, final String fieldName, final Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
