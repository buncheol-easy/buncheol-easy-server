package buncheoleasy.buncheol.dto.response;

import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.user.domain.shipping.ShippingMethod;

/** 마이페이지 참여 목록의 배송 스냅샷 요약. 입금확인 전(스냅샷 미생성)에는 null. */
public record MyParticipationDeliveryResponse(
    Long deliveryId,
    ShippingMethod shippingMethod,
    String storeName,
    String trackingNumber,
    DeliveryStatus status) {

  public static MyParticipationDeliveryResponse from(final Delivery delivery) {
    return new MyParticipationDeliveryResponse(
        delivery.getId(),
        delivery.getShippingMethod(),
        delivery.getStoreName(),
        delivery.getTrackingNumber(),
        delivery.getStatus());
  }
}
