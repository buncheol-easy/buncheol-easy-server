package buncheoleasy.admin.dto.response;

import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.domain.payment.AdminPaymentView;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.ManagementDeliveryResponse;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import java.time.Instant;

/**
 * 관리자 결제 목록의 행 1건. 참여(결제) 정보에 분철·그룹·멤버·배송 컨텍스트를 함께 담아, 프론트가 분철별 관리 API 를 다시 조회하지 않아도 화면을 구성할 수 있게
 * 한다.
 *
 * <p>{@code paymentStatus} 는 파생 상태(환불 필요 포함), {@code status} 는 참여 원본 상태다. {@code confirmedCount} /
 * {@code minHeadcount} 는 "입금확인 완료했지만 최소 인원 도달 전" 안내에 쓰인다.
 */
public record AdminPaymentRecordResponse(
    Long participationId,
    String participantNickname,
    String memberName,
    long amount,
    AdminPaymentStatus paymentStatus,
    ParticipationStatus status,
    ParticipationCancelReason cancelReason,
    Instant dueAt,
    Instant confirmedAt,
    RefundAccountResponse refundAccount,
    ManagementDeliveryResponse delivery,
    Long buncheolId,
    String buncheolTitle,
    BuncheolStatus buncheolStatus,
    String groupName,
    int minHeadcount,
    long confirmedCount) {

  public static AdminPaymentRecordResponse of(
      final AdminPaymentView view, final long confirmedCount) {
    return new AdminPaymentRecordResponse(
        view.participation().getId(),
        view.participant() == null ? null : view.participant().getNickname().value(),
        view.member() == null ? null : view.member().getName(),
        view.participation().getTotalAmount(),
        AdminPaymentStatus.from(view.participation()),
        view.participation().getStatus(),
        view.participation().getCancelReason(),
        view.participation().getDueAt(),
        view.participation().getConfirmedAt(),
        RefundAccountResponse.from(view.participation().getRefundAccount()),
        view.delivery() == null ? null : ManagementDeliveryResponse.from(view.delivery()),
        view.buncheol().getId(),
        view.buncheol().getTitle(),
        view.buncheol().getStatus(),
        view.group().getName(),
        view.buncheol().getMinHeadcount(),
        confirmedCount);
  }
}
