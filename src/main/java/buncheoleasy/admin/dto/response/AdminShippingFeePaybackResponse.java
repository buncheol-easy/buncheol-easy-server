package buncheoleasy.admin.dto.response;

import buncheoleasy.admin.domain.payback.AdminPaybackView;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import java.time.Instant;

/**
 * 관리자 배송비 환급 검수 목록의 행 1건. {@code status} 는 저장 상태(REQUESTED/COMPLETED/REJECTED — 신청 이력이 있는 건만
 * 조회되므로 파생값이 아니다)다. 환급 입금은 참여 시 등록한 환불계좌({@code refundAccount})로 한다.
 */
public record AdminShippingFeePaybackResponse(
    Long participationId,
    String participantNickname,
    String participantName,
    Long buncheolId,
    String buncheolTitle,
    String memberName,
    Long paybackAmount,
    RefundAccountResponse refundAccount,
    String tweetUrl,
    PaybackStatus status,
    Instant requestedAt,
    Instant completedAt,
    String rejectReason) {

  /** @param refundAccount 환급 입금 계좌. <b>정본은 묶음</b>이라 호출부가 배치로 조회해 넘긴다 (P2-c). */
  public static AdminShippingFeePaybackResponse from(
      final AdminPaybackView view, final RefundAccount refundAccount) {
    return new AdminShippingFeePaybackResponse(
        view.participation().getId(),
        view.participant() == null ? null : view.participant().getNickname().value(),
        view.participant() == null ? null : view.participant().getName(),
        view.buncheol().getId(),
        view.buncheol().getTitle(),
        view.member() == null ? null : view.member().getName(),
        view.participation().getPaybackAmount(),
        RefundAccountResponse.from(refundAccount),
        view.participation().getPaybackTweetUrl(),
        view.participation().getPaybackStatus(),
        view.participation().getPaybackRequestedAt(),
        view.participation().getPaybackCompletedAt(),
        view.participation().getPaybackRejectReason());
  }
}
