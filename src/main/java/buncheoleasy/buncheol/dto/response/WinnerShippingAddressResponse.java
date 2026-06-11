package buncheoleasy.buncheol.dto.response;

import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingMethod;

/**
 * 개최자 관리 화면에 노출하는 낙찰자의 최종 선택 배송지.
 *
 * <p>입금 완료 신고(PAYMENT_REPORTED) 시 확정되는 배송지의 라이브 값으로, 입금확인(CONFIRMED) 시점에 박제되는 배송 스냅샷({@link
 * WinnerDeliveryResponse} 의 deliveryId 이하)과 달리 입금확인 전에도 개최자가 발송지를 확인할 수 있게 한다.
 *
 * @param shippingMethod 배송 방법 (GS25_HALF | CU_HALF)
 * @param storeName 수령 매장명
 */
public record WinnerShippingAddressResponse(ShippingMethod shippingMethod, String storeName) {

  public static WinnerShippingAddressResponse from(final ShippingAddress shippingAddress) {
    return new WinnerShippingAddressResponse(
        shippingAddress.getShippingMethod(), shippingAddress.getStoreName());
  }
}
