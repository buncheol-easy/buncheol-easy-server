package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 개최자의 입금확인(AWAITING_PAYMENT → CONFIRMED) 시점에 해당 참여의 배송 정보를 스냅샷으로 생성한다. 참여·배송지·유저 정보를 그 시점 값으로 박제해
 * {@link Delivery} 로 보관하므로 이후 원본 변경에 영향받지 않는다. 호출자 트랜잭션({@link
 * buncheoleasy.buncheol.application.participation.ParticipationService#confirmPayment}) 안에서 실행된다.
 *
 * <p>{@code deliveries.participation_id} 가 UNIQUE 라 같은 참여에 두 번 호출하면 {@code DataIntegrityViolationException}
 * 이 난다. 참여당 입금확인은 1회뿐이라 정상 흐름에선 발생하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DeliverySnapshotCreator {

  private final DeliveryDomainService deliveryDomainService;
  private final ShippingAddressDomainService shippingAddressDomainService;
  private final UserDomainService userDomainService;

  public void create(final Participation participation) {
    ShippingAddress shippingAddress =
        shippingAddressDomainService.getShippingAddress(participation.getShippingAddressId());
    User user = userDomainService.getUser(participation.getParticipantId());

    Delivery delivery =
        Delivery.createSnapshot(
            participation.getId(),
            shippingAddress.getShippingMethod(),
            shippingAddress.getStoreName(),
            user.getNickname().value(),
            user.getPhoneNumber().value());
    deliveryDomainService.createDelivery(delivery);
  }
}
