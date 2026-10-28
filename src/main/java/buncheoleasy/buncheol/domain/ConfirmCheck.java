package buncheoleasy.buncheol.domain;

import java.time.Instant;

/**
 * 회원 개최 입금 수집 중 분철의 진행 확정 판정. 진행 확정 API 의 가드와 관리 화면·「개최한 분철」 목록 응답이 이 판정 하나를 쓴다 — 화면이
 * 따로 판정하면 "버튼은 켜졌는데 누르면 409" 가 생긴다.
 *
 * <p>입금 기한(입금 단계 시작 + 24시간)은 화면 표시용이다. 기한이 지나도 서버가 처리하지 않고, 개최자의 진행 확정 버튼이 켜질 뿐이다.
 */
public enum ConfirmCheck {
  /**
   * 모든 참여가 입금 확인됐다 — 진행 확정 가능. 마지막 묶음 입금 확인이 그 자리에서 진행 확정하므로 정상 흐름에서는 응답에 오지 않는다. 옛
   * 데이터·이상 데이터 대비로 수동 진행 확정을 연다.
   */
  ALL_PAID,
  /** 입금 기한이 지났고 확인된 참여가 있다 — 확인된 참여로만 진행 확정 가능(미확인 참여는 빠진다). */
  DUE_PASSED,
  /** 미확인 참여가 있고 기한 전이다 — 진행 확정 불가. */
  BEFORE_DUE,
  /** 확인된 참여가 없다 — 기한과 무관하게 진행 확정 불가(운영진 경유). */
  NO_CONFIRMED;

  /**
   * @param confirmedCount 입금 확인된 참여 수
   * @param unconfirmedCount 입금 대기·「보냈어요」 참여 수({@code ParticipationStatus.payableStatuses()}).
   *     「살아 있는 참여 − 확인」으로 세면 신청(APPLIED)이 섞일 때 진행 확정 가드와 답이 갈린다
   * @return 입금 수집 중이 아니면 {@code null}
   */
  public static ConfirmCheck of(
      final BuncheolStatus status,
      final long confirmedCount,
      final long unconfirmedCount,
      final Instant paymentDueAt,
      final Instant now) {
    if (status != BuncheolStatus.PAYMENT_COLLECTING) {
      return null;
    }
    if (confirmedCount == 0) {
      return NO_CONFIRMED;
    }
    if (unconfirmedCount == 0) {
      return ALL_PAID;
    }
    // 기한 정각부터 지난 것으로 본다. 기한이 비었으면(데이터 이상) 지나지 않은 것으로 둬 미확인 참여를 빼지 않는다.
    if (paymentDueAt != null && !now.isBefore(paymentDueAt)) {
      return DUE_PASSED;
    }
    return BEFORE_DUE;
  }

  /** 진행 확정 버튼이 켜지는가. */
  public boolean isConfirmable() {
    return this == ALL_PAID || this == DUE_PASSED;
  }
}
