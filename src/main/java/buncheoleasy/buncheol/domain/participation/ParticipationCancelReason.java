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
  // 분철이 취소(호스트 취소 또는 최소 인원 미달/미성사)되어 함께 취소. 입금확인된 참여는 개최자의 수동 환불처리가 필요하다.
  BUNCHEOL_CANCELLED("분철 취소"),
  // C2C 참여자 자발 취소 (docs/46 §5 — 신청~확정 전 자유 / 확정~입금확인 전 허용). 돈이 오가기 전 구간이라 환불 없음.
  USER_CANCELLED("자발 취소"),
  // 개최자가 입금 기한이 지난 묶음을 「제외」 (docs/70 결정 8). C2C 는 자동 만료가 없어 이것이 미입금자를 빼는 유일한
  // 출구다. 묶음 통째로만 적용되고, 확정 슬롯이 하나라도 있으면 열리지 않는다.
  HOST_RELEASED("개최자 제외");

  private final String description;

  /**
   * 이 취소를 <b>참여자 본인이</b> 했는가.
   *
   * <p>🔴 취소에는 성격이 완전히 다른 두 종류가 있다. 참여자 자발 취소는 <b>돈이 오가기 전 구간에만</b>
   * 열린다({@code CANCELLABLE_STATUSES = APPLIED · AWAITING_PAYMENT} — 「보냈어요」 이후에는 스스로 못 뺀다).
   * 나머지 셋은 전부 개최자·시스템이 하는 것이고, 그중에는 <b>이미 돈이 들어온 뒤</b>인 경우가 있다.
   *
   * <p>둘을 같은 {@code CANCELLED} 하나로만 보면 "돌려줄 돈이 있는가" 를 나중에 시각으로 되짚어야 한다.
   * 사유가 이미 답을 갖고 있으므로 여기서 축을 갈라 둔다.
   */
  public boolean isCancelledByParticipant() {
    return switch (this) {
      case USER_CANCELLED -> true;
      case PAYMENT_TIMEOUT, BUNCHEOL_CANCELLED, HOST_RELEASED -> false;
    };
  }

  /**
   * 개최자 <b>또는 시스템</b>이 한 취소. 참여자가 스스로 뺀 것이 아니다.
   *
   * <p>이름에 「시스템」을 넣은 이유 — 실제로 개최자가 한 것은 {@code HOST_RELEASED} 하나뿐이다.
   * {@code PAYMENT_TIMEOUT} 은 만료 스케줄러, {@code BUNCHEOL_CANCELLED} 는 개최자 취소 <b>또는</b>
   * 최소 인원 미달 자동 취소다. 호출부는 이름만 보고 판단하므로 이름이 분류를 담아야 한다.
   *
   * <p>⚠️ 위를 부정으로 정의하지 않고 <b>exhaustive switch</b> 로 둔다 — 사유가 늘면 컴파일 단계에서
   * 드러난다. 부정으로 두면 참여자 측 사유가 하나 늘어도 조용히 「개최자」로 분류된다.
   * ({@code AdminPaymentStatus.from} 이 같은 이유로 같은 처방을 쓴다.)
   */
  public boolean isCancelledByHostOrSystem() {
    return !isCancelledByParticipant();
  }
}
