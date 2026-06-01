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

  private void validateAtLeastOneFeeProvided(
      final Integer gs25ShippingFee, final Integer cuShippingFee) {
    if (gs25ShippingFee == null && cuShippingFee == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_SHIPPING_FEE_REQUIRED);
    }
  }

  private void validateFeeValue(final Integer fee) {
    if (fee != null && fee <= 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_SHIPPING_FEE_INVALID);
    }
  }
}
