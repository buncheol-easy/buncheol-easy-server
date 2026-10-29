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
  // LEGACY: 입금 기한(dueAt = min(참여+10분, deadline)) 내 입금확인이 안 되어 입금 만료 스케줄러가 자동 취소. 마감 시점에
  // 남은 입금확인중 참여도 dueAt 가 이미 지난 상태라 같은 스케줄러가 처리한다. 모집 중이면 멤버 슬롯이 다시 선착순 대상이 된다.
  // C2C 는 자동 만료가 없어 새로 생기지 않는다(docs/70 결정 9) — C2C 행의 이 값은 그 전에 만료된 기록이다.
  PAYMENT_TIMEOUT("입금 시간 초과"),
  // 분철이 취소(개최자 취소, 최소 인원 미달, 운영진 파기)되어 함께 취소. 입금확인된 참여는 개최자의 수동 환불처리가 필요하다.
  BUNCHEOL_CANCELLED("분철 취소"),
  // C2C 참여자 자발 취소 — 신청(APPLIED)에서만 열린다(ParticipationCancellability). 돈이 오가기 전 구간이라 환불 없음.
  // 옛 기록에는 추가 모집(입금 수집 중 신청)의 입금 대기 취소도 있다.
  USER_CANCELLED("자발 취소"),
  // 회원 개최 진행 확정 때 입금이 확인되지 않아 빠짐(입금 대기·「보냈어요」). 개최자의 진행 확정과 같은 트랜잭션에서 분철 범위로 쓴다.
  // 「보냈어요」를 누르고 실제로 보냈다면 환불은 개최자와 운영진이 직접 확인한다.
  PAYMENT_NOT_CONFIRMED("입금 미확인"),
  // 「제외」: 진행 확정 뒤 수락한 추가 참여가 입금 기한이 지나도록 입금 확인이 안 돼 개최자가 묶음을 뺐다. 옛 입금 수집 중 「제외」
  // 기록도 이 값이다(staging).
  HOST_RELEASED("개최자 제외"),
  // 진행 확정 뒤 추가 신청(APPLIED)을 개최자가 수락하지 않았다. 계좌가 공개되기 전이라 돈이 오가지 않았다.
  HOST_REJECTED("개최자 거절");

  private final String description;

  /**
   * 이 취소를 <b>참여자 본인이</b> 했는가.
   *
   * <p>🔴 취소에는 성격이 완전히 다른 두 종류가 있다. 참여자 자발 취소는 <b>돈이 오가기 전 구간에만</b>
   * 열린다(회원 개최는 신청만 — {@code ParticipationCancellability}). 나머지는 전부 개최자·시스템·운영진이 하는 것이고,
   * 그중에는 <b>이미 돈이 들어온 뒤</b>인 경우가 있다(진행 확정 때 빠진 「보냈어요」, 운영진 파기).
   *
   * <p>둘을 같은 {@code CANCELLED} 하나로만 보면 "돌려줄 돈이 있는가" 를 나중에 시각으로 되짚어야 한다.
   * 사유가 이미 답을 갖고 있으므로 여기서 축을 갈라 둔다.
   */
  public boolean isCancelledByParticipant() {
    return switch (this) {
      case USER_CANCELLED -> true;
      case PAYMENT_TIMEOUT, BUNCHEOL_CANCELLED, PAYMENT_NOT_CONFIRMED, HOST_RELEASED, HOST_REJECTED ->
          false;
    };
  }

  /**
   * 개최자 <b>또는 시스템</b>이 한 취소. 참여자가 스스로 뺀 것이 아니다.
   *
   * <p>이름에 「시스템」을 넣은 이유 — 개최자가 직접 고른 취소는 {@code PAYMENT_NOT_CONFIRMED}(진행 확정), {@code
   * HOST_RELEASED}(「제외」), {@code HOST_REJECTED}(추가 신청 거절)뿐이다. {@code PAYMENT_TIMEOUT} 은 만료 스케줄러, {@code BUNCHEOL_CANCELLED} 는 개최자 취소·
   * 최소 인원 미달 자동 취소·운영진 파기다. 호출부는 이름만 보고 판단하므로 이름이 분류를 담아야 한다.
   *
   * <p>⚠️ 위를 부정으로 정의하지 않고 <b>exhaustive switch</b> 로 둔다 — 사유가 늘면 컴파일 단계에서
   * 드러난다. 부정으로 두면 참여자 측 사유가 하나 늘어도 조용히 「개최자」로 분류된다.
   * ({@code AdminPaymentStatus.from} 이 같은 이유로 같은 처방을 쓴다.)
   */
  public boolean isCancelledByHostOrSystem() {
    return !isCancelledByParticipant();
  }
}
