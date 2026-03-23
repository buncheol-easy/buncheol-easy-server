package buncheoleasy.buncheol.domain;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BuncheolStatus {
  RECRUITING("모집중"),
  CLOSED("마감"),
  GOODS_ORDERED("굿즈주문완료"),
  SELLER_SHIPPING("굿즈배송중"),
  HOST_SHIPPING("개최자배송중"),
  ALL_RECEIVED("전원배송완료"),
  SETTLING("개최자정산중"),
  SETTLED("개최자정산완료"),
  FINISHED("종료"),
  CANCELLED("취소");

  private final String value;

  public boolean isCancellable() {
    return switch (this) {
      case RECRUITING, CLOSED, GOODS_ORDERED, SELLER_SHIPPING -> true;
      default -> false;
    };
  }

  // 개최자가 수동으로 진행 가능한 전이 (마감 제외)
  public boolean canAdvanceTo(BuncheolStatus next) {
    return switch (this) {
      case CLOSED -> next == GOODS_ORDERED;
      case GOODS_ORDERED -> next == SELLER_SHIPPING;
      default -> false;
    };
  }
}
