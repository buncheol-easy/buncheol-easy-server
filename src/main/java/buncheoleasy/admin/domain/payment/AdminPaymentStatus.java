package buncheoleasy.admin.domain.payment;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;

/**
 * 관리자 결제 화면의 파생 상태. 참여 상태에 "입금확인 후 분철 취소 = 환불 필요"를 얹어 구분한다.
 *
 * <p>분철이 취소되면 cascade 가 참여를 CANCELLED 로 전이시키되 {@code confirmedAt} 은 남기므로, 취소됐지만 입금확인 이력이 있는 참여가 곧
 * 환불 대상이다. 이름 문자열은 목록 조회 JPQL 의 CASE 분기와 일치해야 한다.
 */
public enum AdminPaymentStatus {
  AWAITING_CONFIRMATION,
  CONFIRMED,
  REFUND_REQUIRED,
  CANCELLED;

  public static AdminPaymentStatus from(final Participation participation) {
    return switch (participation.getStatus()) {
      case AWAITING_PAYMENT -> AWAITING_CONFIRMATION;
      case CONFIRMED -> CONFIRMED;
      // ParticipationStatus 에 값이 추가되면 여기와 목록 JPQL CASE·summarize 집계를 함께 수정해야 한다
      // (exhaustive switch 라 컴파일 단계에서 드러난다).
      case CANCELLED -> participation.getConfirmedAt() != null ? REFUND_REQUIRED : CANCELLED;
    };
  }
}
