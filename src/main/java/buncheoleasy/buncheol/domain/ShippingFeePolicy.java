package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ShippingFeePolicy(
    @Column(name = "gs25_shipping_fee") Integer gs25ShippingFee,
    @Column(name = "cu_shipping_fee") Integer cuShippingFee) {

  public ShippingFeePolicy {
    validateAtLeastOneFeeProvided(gs25ShippingFee, cuShippingFee);
    validateFeeValue(gs25ShippingFee);
    validateFeeValue(cuShippingFee);
  }

  public static ShippingFeePolicy of(final Integer gs25ShippingFee, final Integer cuShippingFee) {
    return new ShippingFeePolicy(gs25ShippingFee, cuShippingFee);
  }

  /** 선택한 배송수단의 배송비. 해당 배송수단을 이 분철이 지원하지 않으면 예외. */
  public long feeFor(final ShippingMethod shippingMethod) {
    Integer fee =
        switch (shippingMethod) {
          case GS25_HALF -> gs25ShippingFee;
          case CU_HALF -> cuShippingFee;
        };
    if (fee == null) {
      throw new BusinessException(ErrorCode.PARTICIPATION_SHIPPING_METHOD_NOT_SUPPORTED);
    }
    return fee;
  }

  /** 이용 가능한 배송수단의 배송비가 모두 0원인가. 등록하지 않은(null) 배송수단은 선택 자체가 불가하므로 판정에서 뺀다. */
  public boolean isFree() {
    return isFreeOrUnsupported(gs25ShippingFee) && isFreeOrUnsupported(cuShippingFee);
  }

  private static boolean isFreeOrUnsupported(final Integer fee) {
    return fee == null || fee == 0;
  }

  private void validateAtLeastOneFeeProvided(
      final Integer gs25ShippingFee, final Integer cuShippingFee) {
    if (gs25ShippingFee == null && cuShippingFee == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_SHIPPING_FEE_REQUIRED);
    }
  }

  // 0원은 개최자가 배송비를 받지 않는 무료 배송으로 허용한다.
  private void validateFeeValue(final Integer fee) {
    if (fee != null && fee < 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_SHIPPING_FEE_INVALID);
    }
  }
}
