package buncheoleasy.buncheol.domain;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuncheolStatus {
  RECRUITING("모집중"),
  // C2C 전용: 개최자가 성사 확정을 눌러 일괄 입금(paymentDueAt)을 수집 중인 상태. 빈 슬롯은 즉시입금
  // 신청으로 추가 모집 가능하며(docs/46 §4.7-E1), 전원 입금확인 시 CONFIRMED 로 전이한다.
  PAYMENT_COLLECTING("입금 수집중"),
  // 마감 시점에 입금확인된 참여자가 최소 인원 이상이라 진행이 확정된 상태. 운영자가 굿즈 구매·배송을 진행한다.
  // C2C 에서는 전원 입금확인(또는 기한 경과 후 개최자 부분 확정) 시 이 상태가 된다.
  CONFIRMED("진행확정"),
  // 마감 시점 최소 인원 미달로 자동 취소된 상태. 목록에 노출된다(참여자에게 "왜 안 열렸는지" 보여주는 용도).
  CANCELLED("취소"),
  // 개최자가 직접 취소한 상태. 목록·상세 어디에도 노출하지 않는다(하드 삭제 대신 기록 보존용 소프트 숨김).
  HOST_CANCELLED("개최자 취소");

  // 활성 분철 집합(취소 상태 CANCELLED/HOST_CANCELLED 전부 제외). 인기 그룹 집계처럼 "성사 가능성이 살아있는 분철" 을 IN 절로 표현할 때 사용한다.
  // 부정 조건보다 IN 으로 표현해야 옵티마이저가 IN-list 카디널리티를 명확히 추정해 status 시작 인덱스(idx_buncheols_status_*) 활용을 안정적으로 선택한다.
  private static final Set<BuncheolStatus> ACTIVE = Set.of(RECRUITING, PAYMENT_COLLECTING, CONFIRMED);

  private final String description;

  public static Set<BuncheolStatus> active() {
    return ACTIVE;
  }
}
