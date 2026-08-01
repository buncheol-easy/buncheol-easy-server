package buncheoleasy.delivery.domain;

import buncheoleasy.user.domain.shipping.ShippingMethod;

/**
 * 추적 중(SHIPPING·DELIVERED)인 운송장 단위 뷰. 관리자 벌크 등록으로 한 운송장에 여러 배송이 매핑될 수 있어, 웹훅 갱신·폴링은 배송이 아닌 운송장
 * 단위로 중복 제거해 돈다.
 */
public record TrackedParcel(ShippingMethod shippingMethod, String trackingNumber) {}
