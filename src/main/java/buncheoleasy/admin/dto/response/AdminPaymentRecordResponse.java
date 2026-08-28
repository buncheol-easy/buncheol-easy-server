package buncheoleasy.admin.dto.response;

import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.domain.payment.AdminPaymentView;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.ManagementDeliveryResponse;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.dto.response.RefundAccountResponse;
import java.time.Instant;

/**
 * 관리자 결제 목록의 행 1건. 참여(결제) 정보에 분철·그룹·멤버·배송 컨텍스트를 함께 담아, 프론트가 분철별 관리 API 를 다시 조회하지 않아도 화면을 구성할 수 있게
 * 한다.
 *
 * <p>{@code paymentStatus} 는 파생 상태(환불 필요 포함), {@code status} 는 참여 원본 상태다. {@code
 * requestedShippingAddress} 는 참여가 선택한 배송지의 현재 원본(입금확인 전 표시용)이고, 확정 배송 정보는 {@code delivery} 스냅샷이 기준이다. {@code confirmedCount} /
 * {@code minHeadcount} 는 "입금확인 완료했지만 최소 인원 도달 전" 안내에 쓰인다.
 */
public record AdminPaymentRecordResponse(
    Long participationId,
    String participantNickname,
    String participantName,
    String memberName,
    long amount,
    AdminPaymentStatus paymentStatus,
    ParticipationStatus status,
    ParticipationCancelReason cancelReason,
    Instant dueAt,
    Instant confirmedAt,
    RefundAccountResponse refundAccount,
    ManagementDeliveryResponse delivery,
    AdminRequestedShippingAddressResponse requestedShippingAddress,
    Long buncheolId,
    String buncheolTitle,
    BuncheolStatus buncheolStatus,
    String groupName,
    int minHeadcount,
    long confirmedCount) {

  /**
   * @param refundAccount 환불 계좌. <b>정본은 묶음</b>이라 호출부가 배치로 조회해 넘긴다 (P2-c). 미연결 참여는
   *     {@code null} 일 수 있다
   */
  public static AdminPaymentRecordResponse of(
      final AdminPaymentView view,
      final long confirmedCount,
      final RefundAccount refundAccount) {
    return new AdminPaymentRecordResponse(
        view.participation().getId(),
        view.participant() == null ? null : view.participant().getNickname().value(),
        view.participant() == null ? null : view.participant().getName(),
        view.member() == null ? null : view.member().getName(),
        view.participation().getTotalAmount(),
        AdminPaymentStatus.from(view.participation()),
        view.participation().getStatus(),
        view.participation().getCancelReason(),
        view.participation().getDueAt(),
        view.participation().getConfirmedAt(),
        RefundAccountResponse.from(refundAccount),
        view.delivery() == null ? null : ManagementDeliveryResponse.from(view.delivery()),
        view.shippingAddress() == null
            ? null
            : AdminRequestedShippingAddressResponse.from(view.shippingAddress()),
        view.buncheol().getId(),
        view.buncheol().getTitle(),
        view.buncheol().getStatus(),
        view.group().getName(),
        view.buncheol().getMinHeadcount(),
        confirmedCount);
  }
}
