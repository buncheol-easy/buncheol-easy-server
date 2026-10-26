package buncheoleasy.push.infrastructure;

/** 기기 한 대로 보낸 결과. */
public enum ApnsSendResult {
  SENT,
  /** 애플이 토큰을 무효로 답함(410 Unregistered · 400 BadDeviceToken) — 그 토큰 행을 지운다. */
  TOKEN_INVALID,
  /** 그 밖의 실패. 로그만 남기고 넘어간다. */
  FAILED,
  /** 설정이 없어 보내지 않음. */
  DISABLED
}
