package buncheoleasy.buncheol.domain;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuncheolStatus {
  RECRUITING("모집중"),
  // 마감 시점에 입금확인된 참여자가 최소 인원 이상이라 진행이 확정된 상태. 운영자가 굿즈 구매·배송을 진행한다.
  CONFIRMED("진행확정"),
  // 호스트 취소 또는 최소 인원 미달로 취소된 상태.
  CANCELLED("취소");

  private static final Set<BuncheolStatus> NOT_CANCELLED = Set.of(RECRUITING, CONFIRMED);

  private final String description;

  // CANCELLED 를 제외한 상태 집합. 공개 목록처럼 "취소되지 않은 분철" 을 IN 절로 표현할 때 사용한다.
  // 부정 조건(status != CANCELLED) 보다 IN 으로 표현해야 옵티마이저가 IN-list 카디널리티를 명확히 추정해
  // status 시작 인덱스(idx_buncheols_status_*) 활용을 안정적으로 선택한다.
  public static Set<BuncheolStatus> notCancelled() {
    return NOT_CANCELLED;
  }
}
