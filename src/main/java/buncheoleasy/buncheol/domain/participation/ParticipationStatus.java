package buncheoleasy.buncheol.domain.participation;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipationStatus {
  // C2C 신청(무입금 슬롯 선점). 개최자 성사 확정 시 일괄로 AWAITING_PAYMENT 로 전이한다. dueAt 없음(NULL).
  APPLIED("신청됨"),
  // 참여(슬롯 점유) 직후. 개최자 계좌가 노출되고 입금 만료(dueAt)까지 호스트의 수동 입금확인을 기다리는 상태.
  // C2C 에서는 성사 확정 후(또는 추가 모집 즉시입금 진입 후) 일괄 기한(24h) 동안 이 상태에 머문다.
  AWAITING_PAYMENT("입금 확인 중"),
  // C2C: 참여자가 "보냈어요" 를 눌러 입금을 주장한 상태. 만료 스케줄러 대상에서 제외되며(기한 직전 입금 보호),
  // 개최자의 입금확인(CONFIRMED) 또는 반려(AWAITING_PAYMENT 복귀 + 기한 연장)를 기다린다.
  PAYMENT_SENT("보냈어요"),
  // 개최자가 입금을 수동 확인한 상태. 분철 진행 최소 인원 카운트 대상이며, 분철 진행 시 배송 대상이 된다.
  CONFIRMED("참여 확정"),
  // 종료 상태. 취소 사유는 ParticipationCancelReason 로 구분한다 (입금 시간 초과 / 자발 취소 / 분철 취소).
  CANCELLED("참여 취소");

  private final String description;

  // 멤버 슬롯을 점유한 '살아있는' 참여 상태들. 선착순 중복 점유 가드(generated column),
  // 분철 취소 시 cascade 대상, 참여자 수 집계의 공통 기준이다. schema.sql 의 generated column
  // 상태 목록과 반드시 일치해야 한다 (docs/46 §2.3-3).
  // 탈퇴 가드는 배송·환급 종료까지 보는 별도 판정(existsUnfinishedByParticipantId)을 쓴다.
  private static final Set<ParticipationStatus> ACTIVE =
      Set.of(APPLIED, AWAITING_PAYMENT, PAYMENT_SENT, CONFIRMED);

  public static Set<ParticipationStatus> active() {
    return ACTIVE;
  }

  // 개최자 「제외」 대상. 활성 상태에서 CONFIRMED 만 뺀 것 — 확정분은 분철 취소 cascade + 환불 경로로만 끝난다
  // (docs/70 결정 8). ACTIVE 에서 파생시켜, 활성 상태가 늘어도 두 목록이 갈리지 않게 한다.
  private static final Set<ParticipationStatus> RELEASABLE =
      ACTIVE.stream()
          .filter(status -> status != CONFIRMED)
          .collect(Collectors.toUnmodifiableSet());

  public static Set<ParticipationStatus> releasableStatuses() {
    return RELEASABLE;
  }
}
