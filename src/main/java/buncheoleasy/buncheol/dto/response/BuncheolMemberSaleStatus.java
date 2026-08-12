package buncheoleasy.buncheol.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 분철 상세 화면에서 멤버 슬롯의 판매 상태. 활성 참여(Participation) 유무와 그 상태로 계산한다. */
@Getter
@RequiredArgsConstructor
public enum BuncheolMemberSaleStatus {
  // 활성 참여가 없어 누구나 참여할 수 있는 공석.
  AVAILABLE("판매중"),
  // C2C: 누군가 무입금 신청으로 선점한 상태. 개최자 성사 확정 시 입금 단계로 넘어간다.
  APPLIED("신청됨"),
  // 누군가 선점해 입금 확인을 기다리는 상태. 입금 기한이 지나면 다시 AVAILABLE 로 풀린다.
  AWAITING_PAYMENT("입금 확인 중"),
  // 입금 확인까지 끝나 판매가 완료된 상태.
  SOLD("판매 완료"),
  // 분철이 더 이상 신규 참여를 받지 않아(진행확정·취소) 닫힌 공석. 점유한 참여는 없지만 신청도 불가능하다 (docs/53 Q-14).
  CLOSED("마감");

  private final String description;
}
