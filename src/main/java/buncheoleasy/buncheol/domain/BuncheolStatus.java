package buncheoleasy.buncheol.domain;

import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BuncheolStatus {
  RECRUITING("모집중"),
  CLOSED("마감"),
  PAID("결제완료"),
  SETTLING("정산중"),
  FINISHED("종료"),
  CANCELLED("취소");

  private static final Set<BuncheolStatus> ACTIVE_OR_FINISHED =
      Set.of(RECRUITING, CLOSED, PAID, SETTLING, FINISHED);

  private final String value;

  // 호스트가 직접 취소 가능한 구간. CLOSED 진입 이후엔 결제·배송이 얽혀 단순 취소가 불가하다.
  public boolean isCancellable() {
    return this == RECRUITING;
  }

  // 호스트가 수동 마감 가능한 구간. RECRUITING → CLOSED 전이는 모집 단계에서만 의미가 있다.
  public boolean isCloseable() {
    return this == RECRUITING;
  }

  // CANCELLED 를 제외한 상태 집합. 인기 집계처럼 "취소되지 않은 분철" 을 IN 절로 표현할 때 사용.
  // 부정 조건(`status != CANCELLED`) 보다 IN 으로 표현해야 옵티마이저가 IN-list 카디널리티를 명확히 추정해
  // status 시작 인덱스(idx_buncheols_status_*) 활용을 안정적으로 선택한다.
  public static Set<BuncheolStatus> activeOrFinished() {
    return ACTIVE_OR_FINISHED;
  }
}
