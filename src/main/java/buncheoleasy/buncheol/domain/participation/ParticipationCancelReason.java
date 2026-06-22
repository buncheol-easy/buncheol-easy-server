package buncheoleasy.buncheol.domain.participation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 참여가 {@link ParticipationStatus#CANCELLED} 로 전이된 사유. 종료 상태를 하나로 두되 사유로 구분해, 유저 안내 메시지와 멤버 슬롯 반환 여부
 * 판단의 근거로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum ParticipationCancelReason {
  // 입금 기한(dueAt = min(참여+30분, deadline)) 내 입금확인이 안 되어 입금 만료 스케줄러가 자동 취소. 마감 시점에 남은 입금확인중
  // 참여도 dueAt 가 이미 지난 상태라 같은 스케줄러가 처리한다. 모집 중이면 멤버 슬롯이 다시 선착순 대상이 된다.
  PAYMENT_TIMEOUT("입금 시간 초과"),
  // 분철이 취소(호스트 취소 또는 최소 인원 미달)되어 함께 취소. 입금확인된 참여는 개최자의 수동 환불처리가 필요하다.
  BUNCHEOL_CANCELLED("분철 취소");

  private final String description;
}
