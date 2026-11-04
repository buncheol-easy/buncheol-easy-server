package buncheoleasy.push.domain;

/**
 * 기기 토큰이 발급된 APNs 환경. 개발 빌드의 토큰은 SANDBOX 서버로, 스토어·TestFlight 빌드의 토큰은 PRODUCTION 서버로만 보낼 수 있다 — 반대로
 * 보내면 애플이 BadDeviceToken 으로 거절한다.
 */
public enum PushEnvironment {
  SANDBOX,
  PRODUCTION
}
