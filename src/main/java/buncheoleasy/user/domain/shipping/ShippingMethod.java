package buncheoleasy.user.domain.shipping;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShippingMethod {
  GS25_HALF("GS25반값택배"),
  CU_HALF("CU반값택배");

  private final String description;

  public static ShippingMethod of(final String name) {
    for (ShippingMethod method : ShippingMethod.values()) {
      if (method.name().equals(name)) {
        return method;
      }
    }
    throw new BusinessException(ErrorCode.SHIPPING_METHOD_FORMAT_INVALID);
  }
}
