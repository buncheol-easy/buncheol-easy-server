package buncheoleasy.buncheol.dto.response;

import buncheoleasy.user.domain.shipping.ShippingMethod;

/** 분철이 지원하는 배송방법과 해당 배송비. */
public record ShippingOptionResponse(ShippingMethod method, int fee) {}
