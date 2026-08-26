package buncheoleasy.buncheol.domain.code;

/** 참여 코드 사용 가능 여부와 사유. 사유마다 사용자가 할 일이 달라 불리언 대신 값으로 표현한다. */
public enum CodeRedeemability {
  REDEEMABLE,
  SLOT_MISMATCH,
  REVOKED,
  ALREADY_USED,
  EXPIRED
}
