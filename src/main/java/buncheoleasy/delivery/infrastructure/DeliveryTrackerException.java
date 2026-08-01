package buncheoleasy.delivery.infrastructure;

/** Delivery Tracker 호출 실패. 자동 추적은 보조 수단이라 호출 측이 잡아 로깅만 하고 비즈니스 트랜잭션에는 전파하지 않는다. */
public class DeliveryTrackerException extends RuntimeException {

  public DeliveryTrackerException(final String message) {
    super(message);
  }

  public DeliveryTrackerException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
