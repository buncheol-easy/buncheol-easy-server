package buncheoleasy.buncheol.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 분철 상세 화면에서 멤버 슬롯의 판매 상태. 활성 참여(Participation) 유무와 그 상태로 계산한다. */
@Getter
@RequiredArgsConstructor
public enum BuncheolMemberSaleStatus {
  // 활성 참여가 없어 누구나 참여할 수 있는 공석.
  AVAILABLE("판매중"),
  // 누군가 선점해 입금 확인을 기다리는 상태. 입금 기한이 지나면 다시 AVAILABLE 로 풀린다.
  AWAITING_PAYMENT("입금 확인 중"),
  // 입금 확인까지 끝나 판매가 완료된 상태.
  SOLD("판매 완료");

  private final String description;
}
