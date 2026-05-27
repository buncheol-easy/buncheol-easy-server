package buncheoleasy.buncheol.domain;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BuncheolStatus {
  RECRUITING("모집중"),
  CLOSED("마감"),
  PAID("결제완료"),
  SETTLING("정산중"),
  FINISHED("종료"),
  CANCELLED("취소");

  private final String value;

  // 호스트가 직접 취소 가능한 구간. CLOSED 진입 이후엔 결제·배송이 얽혀 단순 취소가 불가하다.
  public boolean isCancellable() {
    return this == RECRUITING;
  }

  // 호스트가 수동 마감 가능한 구간. RECRUITING → CLOSED 전이는 모집 단계에서만 의미가 있다.
  public boolean isCloseable() {
    return this == RECRUITING;
  }
}
