package buncheoleasy.auth.domain;

/**
 * 애플 로그인 실패 제한(IP 단위). 성공은 세지 않는다 — 앱은 통신사 공용 IP 로 여러 단말이 묶일 수 있어 시도 수로 막으면 정상 사용자가 걸린다. 유효한
 * 애플 토큰은 위조할 수 없으므로 막을 것은 검증 실패의 반복이다.
 */
public interface AppleLoginAttemptLimiter {

  /** 이 IP 의 최근 실패가 한도에 닿았으면 거절한다(429 AUTH-012). */
  void checkAllowed(String clientIp);

  void recordFailure(String clientIp);
}
