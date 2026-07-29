package buncheoleasy.buncheol.domain.participation;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipationStatus {
  // 참여(슬롯 점유) 직후. 개최자 계좌가 노출되고 입금 만료(dueAt)까지 호스트의 수동 입금확인을 기다리는 상태.
  AWAITING_PAYMENT("입금 확인 중"),
  // 개최자가 입금을 수동 확인한 상태. 분철 진행 최소 인원 카운트 대상이며, 분철 진행 시 배송 대상이 된다.
  CONFIRMED("참여 확정"),
  // 종료 상태. 취소 사유는 ParticipationCancelReason 로 구분한다 (입금 시간 초과 / 자발 취소 / 모집 마감 / 분철 취소).
  CANCELLED("참여 취소");

  private final String description;

  // 멤버 슬롯을 점유한 '살아있는' 참여 상태들. 선착순 중복 점유 가드(generated column),
  // 분철 취소 시 cascade 대상, 참여자 수 집계의 공통 기준이다.
  // 탈퇴 가드는 배송·환급 종료까지 보는 별도 판정(existsUnfinishedByParticipantId)을 쓴다.
  private static final Set<ParticipationStatus> ACTIVE = Set.of(AWAITING_PAYMENT, CONFIRMED);

  public static Set<ParticipationStatus> active() {
    return ACTIVE;
  }
}
