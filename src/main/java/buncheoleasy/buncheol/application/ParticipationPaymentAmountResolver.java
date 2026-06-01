package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 낙찰자가 결제해야 할 금액(제시가 + 배송비)을 단건 참여 기준으로 산정한다. */
@Component
@RequiredArgsConstructor
public class ParticipationPaymentAmountResolver {

  private final BuncheolDomainService buncheolDomainService;
  private final ShippingAddressDomainService shippingAddressDomainService;

  public long resolve(final Participation participation) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    ShippingAddress shippingAddress =
        shippingAddressDomainService.getShippingAddress(participation.getShippingAddressId());
    return participation.getBidAmount()
        + buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
  }
}
