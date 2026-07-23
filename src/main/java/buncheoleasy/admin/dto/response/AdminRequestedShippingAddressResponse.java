package buncheoleasy.admin.dto.response;

import buncheoleasy.user.domain.shipping.ShippingAddress;

/**
 * 관리자 결제 목록의 "결제 요청 배송지" — 참여자가 참여 시점에 선택한 배송지의 현재 원본 값이다. 입금확인 전(배송 스냅샷 생성 전)에도 운영자가 배송지를
 * 확인할 수 있게 노출한다. 배송지 원본은 유저가 수정할 수 있으므로 확정 배송 정보는 입금확인 시 박제되는 {@code delivery} 스냅샷이 기준이다.
 */
public record AdminRequestedShippingAddressResponse(String shippingMethod, String storeName) {

  public static AdminRequestedShippingAddressResponse from(final ShippingAddress shippingAddress) {
    return new AdminRequestedShippingAddressResponse(
        shippingAddress.getShippingMethod().name(), shippingAddress.getStoreName());
  }
}
