package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 참여자가 선택한 배송지가 본인 소유이며 해당 분철이 지원하는 배송 방법인지 검증하고 반환한다. 참여 신청(입찰) 과 입금 완료 신고(최종 배송지 확정) 가 동일한 규칙을
 * 공유한다.
 */
@Component
@RequiredArgsConstructor
public class ParticipationShippingAddressResolver {

  private final ShippingAddressDomainService shippingAddressDomainService;

  public ShippingAddress resolve(
      final Long participantId, final Buncheol buncheol, final Long shippingAddressId) {
    ShippingAddress shippingAddress =
        shippingAddressDomainService.getShippingAddress(shippingAddressId);
    if (!shippingAddress.isOwnedBy(participantId)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
    }
    buncheol.validateShippingMethodSupported(shippingAddress.getShippingMethod());
    return shippingAddress;
  }
}
