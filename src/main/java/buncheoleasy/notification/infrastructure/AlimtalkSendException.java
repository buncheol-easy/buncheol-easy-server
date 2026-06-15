package buncheoleasy.notification.infrastructure;

/** 알림톡 발송 실패. 비동기 리스너에서 포착·로깅되며 비즈니스 트랜잭션으로 전파되지 않는다. */
public class AlimtalkSendException extends RuntimeException {

  public AlimtalkSendException(final String message) {
    super(message);
  }

  public AlimtalkSendException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
