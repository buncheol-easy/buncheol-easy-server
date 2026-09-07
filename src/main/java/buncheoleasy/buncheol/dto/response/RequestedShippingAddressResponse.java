package buncheoleasy.buncheol.dto.response;

import buncheoleasy.user.domain.shipping.ShippingAddress;

/**
 * 참여자에게 보여 주는 <b>이 묶음이 실제로 쓰는 배송지</b>. 택배 1개 = 묶음 1개라 한 묶음의 슬롯은 모두 같은 주소로 간다.
 *
 * <p>🔴 <b>왜 서버가 내려야 하는가.</b> 화면이 이 값을 유저의 배송지 목록에서 고르면(기본 배송지 폴백 등) <b>틀린 주소를
 * 확신에 차서</b> 보여 주게 된다 — 「배송지 고정 · 변경 불가」 라벨이 붙는 자리라 "정보가 없음" 보다 나쁘다. 묶음의 배송지는
 * 유저가 나중에 다른 주소를 기본으로 바꿔도 그대로이므로, 목록에서 재구성할 방법이 아예 없다.
 *
 * <p>입금확인 뒤 확정 배송 정보는 스냅샷({@code delivery})이 기준이다. 이 값은 그 전 구간의 <b>표시용</b>이며 배송지 원본을
 * 그대로 읽으므로 유저가 지점명을 수정하면 따라 바뀐다. 어드민의 {@code AdminRequestedShippingAddressResponse} 와 같은 규약이다.
 */
public record RequestedShippingAddressResponse(String shippingMethod, String storeName) {

  public static RequestedShippingAddressResponse from(final ShippingAddress shippingAddress) {
    return new RequestedShippingAddressResponse(
        shippingAddress.getShippingMethod().name(), shippingAddress.getStoreName());
  }
}
