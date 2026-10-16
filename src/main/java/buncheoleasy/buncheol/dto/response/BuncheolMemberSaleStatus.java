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
  // 누군가 선점해 입금 확인을 기다리는 상태(C2C 「보냈어요」 포함). 슬롯이 풀리면 분철이 그때도 신규 참여를 받는 중이면
  // AVAILABLE 로, 아니면 CLOSED 로 돌아간다. LEGACY 는 입금 기한이 지나면 자동으로 풀리고(기한이 마감 시각으로 클램프돼
  // CLOSED 로 가는 경로가 흔하다), C2C 는 기한이 지나도 개최자 「제외」 전까지 풀리지 않는다.
  AWAITING_PAYMENT("입금 확인 중"),
  // 입금 확인까지 끝나 판매가 완료된 상태.
  SOLD("판매 완료"),
  // 분철이 더 이상 신규 참여를 받지 않아(진행확정·취소) 닫힌 공석. 점유한 참여는 없지만 신청도 불가능하다 (docs/53 Q-14).
  // C2C 개최자가 「빼기」로 닫은 자리(접근 정책 CLOSED)도 이 값이다 — 신청을 받는 분철의 빈 자리가 CLOSED 면 그 경우뿐이다.
  CLOSED("마감"),
  // 코드 보유자에게 배정된 공석. CLOSED 로 뭉뚱그리면 오픈 직후부터 절반이 마감으로 보여 조작 의심을 부른다.
  CODE_ONLY("서포터즈 배정"),
  // 예약 오픈 전인 운영진 분철의 선착순 빈 자리. 오픈 시각이 되면 AVAILABLE 이 된다. 코드 전용 자리는 오픈 전에도 CODE_ONLY 다.
  NOT_OPEN("오픈 전");

  private final String description;
}
