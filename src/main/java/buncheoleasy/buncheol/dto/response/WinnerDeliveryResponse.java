package buncheoleasy.buncheol.dto.response;

import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.user.domain.shipping.ShippingMethod;

/**
 * 개최자 분철 관리 화면의 옵션별 낙찰자 배송 정보.
 *
 * <p>모든 필드는 분철 마감 시점에 찍힌 스냅샷이라 이후 낙찰자가 닉네임·배송지를 바꿔도 영향받지 않는다.
 *
 * @param trackingNumber 호스트가 등록한 운송장 번호. 미등록 시 null
 */
public record WinnerDeliveryResponse(
    Long deliveryId,
    ShippingMethod shippingMethod,
    String storeName,
    String receiverNickname,
    String receiverPhoneNumber,
    String trackingNumber,
    DeliveryStatus deliveryStatus) {}
