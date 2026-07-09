package buncheoleasy.admin.domain.payment;

/** 관리자 결제 대시보드 상단 통계. 파생 상태({@link AdminPaymentStatus}) 기준 건수와 확인 대기 금액(배송비 포함) 합계. */
public record AdminPaymentSummary(
    long awaitingCount,
    long confirmedCount,
    long refundRequiredCount,
    long cancelledCount,
    long totalCount,
    long awaitingAmount) {}
