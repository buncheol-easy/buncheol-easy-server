package buncheoleasy.buncheol.domain.participation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 오픈 이벤트 배송비 환급(배송비 돌려받기) 상태.
 *
 * <p>DB 에는 {@link #NONE}/{@link #REQUESTED}/{@link #COMPLETED}/{@link #REJECTED} 만 저장한다. {@link
 * #ELIGIBLE}/{@link #EXPIRED} 는 조회 시점에 파생되는 응답 전용 값이라 절대 영속화하지 않는다 — 이벤트 대상 여부·신청 마감이 서버
 * 설정(환경변수)으로 관리되므로, 저장하면 설정 변경 시 DB 재동기화가 필요해진다. 파생 규칙은 {@code ShippingFeePaybackPolicy} 가 단독
 * 소유한다.
 *
 * <p>{@link #APPROVED} 는 승인(입금 대기) 중간 단계용으로 예약만 해둔 값이다. 현재 운영 정책은 승인·입금완료를 한 번에 처리하므로
 * ({@code REQUESTED → COMPLETED} 직행) 사용하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum PaybackStatus {
  /** 이벤트 대상이 아니거나 아직 신청 조건(배송 완료)을 채우지 못한 기본값. 유일하게 저장되는 "신청 전" 상태. */
  NONE("대상 아님"),
  /** (파생 전용) 이벤트 대상 + 배송 완료 + 신청 마감 전 — 신청 가능. */
  ELIGIBLE("신청 가능"),
  /** 유저가 후기 트윗 URL 을 제출해 운영진 검수를 기다리는 상태. */
  REQUESTED("확인 중"),
  /** (예약, 미사용) 후기 승인 후 입금 대기. */
  APPROVED("입금 대기"),
  /** 운영진이 환급 입금을 완료한 종료 상태. */
  COMPLETED("환급 완료"),
  /** 운영진이 후기를 반려한 상태. 재신청할 수 있다. */
  REJECTED("반려"),
  /** (파생 전용) 신청 마감이 지나도록 신청하지 않아 만료. */
  EXPIRED("기한 만료");

  private final String description;

  /**
   * 유저가 후기 트윗 URL 을 제출할 수 있는 저장 상태인지 (신규 신청 = NONE, 반려 후 재신청 = REJECTED, 잘못 올린 링크 수정 =
   * REQUESTED). 입금이 끝난 COMPLETED 만 제출을 닫는다.
   */
  public boolean requestable() {
    return this == NONE || this == REJECTED || this == REQUESTED;
  }
}
