package buncheoleasy.feedback.domain;

/** 의견 보내기 도배 방지. 비로그인도 열려 있는 엔드포인트라 제출 주체(회원 또는 IP)별로 제한한다. */
public interface FeedbackRateLimiter {

  /**
   * 제출 1건을 기록하고 한도를 초과했으면 거부한다.
   *
   * @param clientKey 제출 주체 식별자 (로그인 회원은 회원 ID, 비로그인은 클라이언트 IP)
   * @throws buncheoleasy.global.exception.domain.BusinessException 한도 초과 시 {@code FDB-001}
   */
  void checkAndRecord(String clientKey);
}
