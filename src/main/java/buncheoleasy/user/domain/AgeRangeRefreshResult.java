package buncheoleasy.user.domain;

/** 연령대 추가 동의 결과를 반영한 뒤의 회원 상태. 카카오가 값을 줬는지가 아니라 실제로 저장된 값으로 판정한다. */
public enum AgeRangeRefreshResult {
  // 동의한 카카오 계정으로 가입한 회원이 없다 — 동의만 더하는 흐름이라 새로 만들지 않는다.
  NO_MEMBER,
  PRESENT,
  // 카카오가 값을 주지 않았거나 철회 신호로 파기했거나 저장 규칙에 맞지 않아 연령대가 없다.
  ABSENT
}
