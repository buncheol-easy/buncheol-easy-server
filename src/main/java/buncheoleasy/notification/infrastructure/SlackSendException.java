package buncheoleasy.notification.infrastructure;

/** 슬랙 웹훅 발송 실패. 비동기 리스너에서 포착·로깅되며 비즈니스 트랜잭션으로 전파되지 않는다. */
public class SlackSendException extends RuntimeException {

  public SlackSendException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
