package buncheoleasy.buncheol.dto.response;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.user.domain.shipping.ShippingMethod;

/** 운영자 관리 화면의 배송 스냅샷. 진행확정된 분철의 입금확인 참여에만 생성된다(그 전엔 null). */
public record ManagementDeliveryResponse(
    Long deliveryId,
    ShippingMethod shippingMethod,
    String storeName,
    String receiverNickname,
    String receiverPhoneNumber,
    String trackingNumber,
    DeliveryStatus status) {

  public static ManagementDeliveryResponse from(final Delivery delivery) {
    return new ManagementDeliveryResponse(
        delivery.getId(),
        delivery.getShippingMethod(),
        delivery.getStoreName(),
        delivery.getReceiverNickname(),
        delivery.getReceiverPhoneNumber(),
        delivery.getTrackingNumber(),
        delivery.getStatus());
  }
}
