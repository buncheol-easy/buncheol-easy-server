package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.PhoneNumber;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 배송 스냅샷이 <b>묶음</b>을 물고 만들어지는지 검증한다.
 *
 * <p>택배 1개 = 묶음 1개다. 안 물리면 참여는 묶음을 갖는데 배송만 미연결로 남고, 그건 P4 의 {@code uq_deliveries_bundle}
 * 승격에서야 발견된다 — staging 에서 실제로 3건이 그렇게 샜다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeliverySnapshotCreator 단위 테스트")
class DeliverySnapshotCreatorTest {

  private static final Long PARTICIPATION_ID = 500L;
  private static final Long BUNDLE_ID = 900L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;

  @InjectMocks private DeliverySnapshotCreator deliverySnapshotCreator;

  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private ShippingAddressDomainService shippingAddressDomainService;
  @Mock private UserDomainService userDomainService;

  @Captor private ArgumentCaptor<Delivery> deliveryCaptor;

  private Participation participation(final Long bundleId) {
    Participation participation = newInstance(Participation.class);
    setField(participation, "id", PARTICIPATION_ID);
    setField(participation, "participantId", PARTICIPANT_ID);
    setField(participation, "shippingAddressId", SHIPPING_ADDRESS_ID);
    setField(participation, "bundleId", bundleId);
    return participation;
  }

  private void givenSnapshotSources() {
    given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID))
        .willReturn(
            new ShippingAddress(
                SHIPPING_ADDRESS_ID,
                PARTICIPANT_ID,
                ShippingMethod.GS25_HALF,
                "GS25 강남역점",
                null,
                true));
    User user = newInstance(User.class);
    setField(user, "id", PARTICIPANT_ID);
    setField(user, "nickname", Nickname.of("장원영"));
    setField(user, "phoneNumber", PhoneNumber.of("01012345678"));
    setField(user, "profileCompleted", true);
    given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(user);
  }

  @Test
  void 배송_스냅샷은_참여의_묶음을_물고_생성된다() {
    givenSnapshotSources();

    deliverySnapshotCreator.create(participation(BUNDLE_ID));

    then(deliveryDomainService).should().createDelivery(deliveryCaptor.capture());
    assertThat(deliveryCaptor.getValue().getBundleId()).isEqualTo(BUNDLE_ID);
    assertThat(deliveryCaptor.getValue().getParticipationId()).isEqualTo(PARTICIPATION_ID);
  }

  // 배포선 창에서 생긴 미연결 참여. 배송도 미연결로 만들되 예외는 아니다 — 백필이 둘 다 채운다.
  @Test
  void 묶음이_없는_참여의_배송은_묶음_없이_생성된다() {
    givenSnapshotSources();

    deliverySnapshotCreator.create(participation(null));

    then(deliveryDomainService).should().createDelivery(deliveryCaptor.capture());
    assertThat(deliveryCaptor.getValue().getBundleId()).isNull();
  }

  private static <T> T newInstance(final Class<T> type) {
    try {
      Constructor<T> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
