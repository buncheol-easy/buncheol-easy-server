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
 * 분철 진행확정 시점에 입금확인된(CONFIRMED) 참여의 배송 정보를 스냅샷으로 생성한다. 참여·배송지·유저 정보를 그 시점 값으로 박제해 {@link Delivery} 로
 * 보관하므로 이후 원본 변경에 영향받지 않는다. 호출자 트랜잭션({@link BuncheolAutoCloseService#finalizeExpired}) 안에서 실행된다.
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
