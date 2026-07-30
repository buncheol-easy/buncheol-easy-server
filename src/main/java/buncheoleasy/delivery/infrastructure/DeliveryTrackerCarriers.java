package buncheoleasy.delivery.infrastructure;

import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.util.Optional;

/** 배송 방법 ↔ Delivery Tracker 캐리어 ID 매핑 — 외부 고유 식별자라 도메인 enum 이 아닌 연동 계층에 둔다. */
public final class DeliveryTrackerCarriers {

  private static final String CU = "kr.cupost";
  private static final String GS25 = "kr.cvsnet";

  private DeliveryTrackerCarriers() {}

  public static String carrierId(final ShippingMethod shippingMethod) {
    return switch (shippingMethod) {
      case CU_HALF -> CU;
      case GS25_HALF -> GS25;
    };
  }

  /** 콜백으로 받은 캐리어 ID 의 역매핑. 우리가 등록하지 않은 캐리어면 empty. */
  public static Optional<ShippingMethod> shippingMethod(final String carrierId) {
    return switch (carrierId) {
      case CU -> Optional.of(ShippingMethod.CU_HALF);
      case GS25 -> Optional.of(ShippingMethod.GS25_HALF);
      case null, default -> Optional.empty();
    };
  }
}
