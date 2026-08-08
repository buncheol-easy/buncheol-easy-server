package buncheoleasy.delivery.infrastructure;

/** Delivery Tracker 쿼터 초과(rate limit) 응답. 짧은 백오프 후 재시도로 해소될 수 있는 일시 실패라 일반 실패와 구분한다. */
public class DeliveryTrackerRateLimitException extends DeliveryTrackerException {

  public DeliveryTrackerRateLimitException(final String message) {
    super(message);
  }

  public DeliveryTrackerRateLimitException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
