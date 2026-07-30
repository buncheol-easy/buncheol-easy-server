package buncheoleasy.delivery.infrastructure;

/**
 * Delivery Tracker 추적 조회·웹훅 등록 호출 실패. 자동 추적은 보조 수단이고 실패해도 참여자 수령확인 버튼·갱신 스케줄러 재시도 경로가 남아 있으므로, 호출
 * 측(비동기 리스너·스케줄러)이 잡아 로깅만 하고 비즈니스 트랜잭션에는 전파하지 않는다.
 */
public class DeliveryTrackerException extends RuntimeException {

  public DeliveryTrackerException(final String message) {
    super(message);
  }

  public DeliveryTrackerException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
