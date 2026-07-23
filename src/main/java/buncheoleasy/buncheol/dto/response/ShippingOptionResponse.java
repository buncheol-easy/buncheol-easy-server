package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.ArrayList;
import java.util.List;

/** 분철이 지원하는 배송방법과 해당 배송비. */
public record ShippingOptionResponse(ShippingMethod method, int fee) {

  /** 배송비 정책에서 지원하는 배송방법만 옵션으로 변환한다. */
  public static List<ShippingOptionResponse> listFrom(final ShippingFeePolicy policy) {
    List<ShippingOptionResponse> options = new ArrayList<>(2);
    if (policy.gs25ShippingFee() != null) {
      options.add(new ShippingOptionResponse(ShippingMethod.GS25_HALF, policy.gs25ShippingFee()));
    }
    if (policy.cuShippingFee() != null) {
      options.add(new ShippingOptionResponse(ShippingMethod.CU_HALF, policy.cuShippingFee()));
    }
    return options;
  }
}
