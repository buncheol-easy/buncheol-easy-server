package buncheoleasy.buncheol.domain.participation;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipationStatus {
  // C2C 신청(무입금 슬롯 선점). 입금 단계 시작 때 일괄로 AWAITING_PAYMENT 로 전이한다. dueAt 없음(NULL).
  APPLIED("신청됨"),
  // 입금 대기. 개최자 계좌가 노출되고 개최자의 입금확인을 기다린다.
  // LEGACY 는 유상 참여 즉시 이 상태이고(0원 참여는 바로 CONFIRMED) 기한(10분)이 지나면 만료 스케줄러가 취소한다
  // (PAYMENT_TIMEOUT). C2C 는 입금 단계 시작 일괄 전이로 들어오며(기한 24h), 기한이 지나도 자동 취소하지
  // 않는다 — 입금이 확인되지 않은 채 빠지는 출구는 개최자의 진행 확정(PAYMENT_NOT_CONFIRMED)과 운영진 파기다.
  AWAITING_PAYMENT("입금 확인 중"),
  // C2C: 참여자가 묶음 단위로 "보냈어요" 를 눌러 입금을 주장한 상태. 입금 대기로 되돌리는 경로는 없다.
  PAYMENT_SENT("보냈어요"),
  // 개최자가 입금을 수동 확인한 상태. 분철 진행 최소 인원 카운트 대상이며, 분철 진행 시 배송 대상이 된다.
  CONFIRMED("참여 확정"),
  // 종료 상태. 취소 사유는 ParticipationCancelReason 로 구분한다.
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

  // 아직 확정 전이라 무조건 「끝나지 않음」인 상태들. 확정(CONFIRMED)은 종착 상태라 배송 종료까지
  // 봐야 하므로 가드(탈퇴·배송지 삭제)가 따로 판정한다.
  private static final Set<ParticipationStatus> UNCONFIRMED_ACTIVE =
      ACTIVE.stream()
          .filter(status -> status != CONFIRMED)
          .collect(Collectors.toUnmodifiableSet());

  public static Set<ParticipationStatus> unconfirmedActive() {
    return UNCONFIRMED_ACTIVE;
  }

  // 개최자가 입금확인할 수 있는 상태. C2C 는 「보냈어요」 마킹 여부와 무관하게 확인 가능하다 — 마킹은 단서일
  // 뿐이고 실제 판단 근거는 개최자 통장이다 (docs/46 §3-6).
  private static final Set<ParticipationStatus> PAYABLE = Set.of(AWAITING_PAYMENT, PAYMENT_SENT);

  public static Set<ParticipationStatus> payableStatuses() {
    return PAYABLE;
  }
}
