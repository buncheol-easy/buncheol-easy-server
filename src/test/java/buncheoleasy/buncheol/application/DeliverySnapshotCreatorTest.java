package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
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
import java.util.Optional;
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
  private static final Long STALE_COPY_ADDRESS_ID = 999L;

  @InjectMocks private DeliverySnapshotCreator deliverySnapshotCreator;

  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private ShippingAddressDomainService shippingAddressDomainService;
  @Mock private UserDomainService userDomainService;

  @Captor private ArgumentCaptor<Delivery> deliveryCaptor;

  private Participation participation(final Long bundleId) {
    Participation participation = newInstance(Participation.class);
    setField(participation, "id", PARTICIPATION_ID);
    setField(participation, "participantId", PARTICIPANT_ID);
    // 🔴 참여 사본에는 <b>다른 값</b>을 심는다. 같은 값이면 묶음을 읽든 사본을 읽든 초록이라
    // 이관이 됐는지 테스트가 말해 주지 못한다. 사본은 신규 행에서 어차피 NULL 이 된다.
    setField(participation, "shippingAddressId", STALE_COPY_ADDRESS_ID);
    setField(participation, "bundleId", bundleId);
    return participation;
  }

  private void givenSnapshotSources() {
    // 배송지 정본은 묶음이다 — 이 스텁이 없으면 목이 null 을 줘 조회가 죽는다.
    given(participationBundleDomainService.shippingAddressIdOf(any()))
        .willReturn(SHIPPING_ADDRESS_ID);
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
    given(deliveryDomainService.findByBundleId(BUNDLE_ID)).willReturn(Optional.empty());
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

  // 🔴 다슬롯 묶음은 슬롯마다 입금확인이 돌지만 택배는 하나다. 슬롯마다 만들면 개최자에게 같은 주소의
  // 운송장 입력칸이 슬롯 수만큼 뜨고, P4 의 uq_deliveries_bundle 승격이 그 자리에서 실패한다
  // (실측: prod 묶음 64 · staging 66·83·87 이 그렇게 생겼다).
  @Test
  void 그_묶음의_택배가_이미_있으면_두_번째_슬롯은_만들지_않는다() {
    given(deliveryDomainService.findByBundleId(BUNDLE_ID))
        .willReturn(Optional.of(newInstance(Delivery.class)));

    deliverySnapshotCreator.create(participation(BUNDLE_ID));

    then(deliveryDomainService).should(never()).createDelivery(any());
    // 배송지·유저 조회도 하지 않는다 — 다슬롯 묶음의 두 번째 슬롯부터는 헛쿼리다.
    then(shippingAddressDomainService).shouldHaveNoInteractions();
    then(userDomainService).shouldHaveNoInteractions();
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
