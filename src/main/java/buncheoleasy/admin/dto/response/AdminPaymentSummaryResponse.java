package buncheoleasy.admin.dto.response;

import buncheoleasy.admin.domain.payment.AdminPaymentSummary;

/** 관리자 결제 대시보드 상단 통계. {@code awaitingAmount} 는 확인 대기 건의 입금 총액(멤버 금액+배송비) 합계다. */
public record AdminPaymentSummaryResponse(
    long awaitingCount,
    long confirmedCount,
    long refundRequiredCount,
    long cancelledCount,
    long totalCount,
    long awaitingAmount) {

  public static AdminPaymentSummaryResponse from(final AdminPaymentSummary summary) {
    return new AdminPaymentSummaryResponse(
        summary.awaitingCount(),
        summary.confirmedCount(),
        summary.refundRequiredCount(),
        summary.cancelledCount(),
        summary.totalCount(),
        summary.awaitingAmount());
  }
}
