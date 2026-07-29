package buncheoleasy.deposit.infrastructure;

/**
 * 페이액션 주문 등록·매칭제외 호출 실패. 입금 자동확인은 보조 수단이고 실패해도 운영자 수동 확인 경로가 남아 있으므로, 호출 측(비동기 리스너)이 잡아 로깅만 하고
 * 참여 트랜잭션에는 전파하지 않는다.
 */
public class PayActionSendException extends RuntimeException {

  public PayActionSendException(final String message) {
    super(message);
  }

  public PayActionSendException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
