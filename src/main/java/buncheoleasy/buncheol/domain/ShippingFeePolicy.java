package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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
