package buncheoleasy.buncheol.application.participation;

/**
 * 자동 입금확인(외부 입금 연동 웹훅)의 처리 결과. 수동 입금확인이 실패를 예외로 던지는 것과 달리, 자동 경로는 결과를 구분해 돌려준다 — 웹훅은 재전송되므로
 * "이미 처리된 건"과 "사람이 봐야 하는 건"을 호출 측이 다르게 다뤄야 한다.
 */
public enum SystemPaymentConfirmResult {
  /** 이번 호출로 입금확인 전이에 성공했다. */
  CONFIRMED,
  /** 이미 확정된 참여다. 웹훅 재전송이나 운영자 수동 확인이 앞선 경우이며, 정상 처리로 간주한다. */
  ALREADY_CONFIRMED,
  /**
   * 입금 기한이 지나 취소됐거나 분철이 취소돼 확정할 수 없다. 돈은 들어왔는데 참여가 없는 상태라 환불 판단이 필요하므로 운영자에게 알려야 한다.
   */
  NOT_CONFIRMABLE
}
