package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.application.HostPaymentService;
import buncheoleasy.buncheol.application.MyParticipationQueryService;
import buncheoleasy.buncheol.application.ParticipationPaymentQueryService;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.buncheol.dto.response.ParticipationPaymentDetailResponse;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.dto.response.CreatePaymentOrderResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/participations")
@RequiredArgsConstructor
public class ParticipationController {

  private final BuncheolCheckoutService buncheolCheckoutService;
  private final HostPaymentService hostPaymentService;
  private final ParticipationPaymentQueryService participationPaymentQueryService;
  private final MyParticipationQueryService myParticipationQueryService;

  /** 분철 낙찰자 결제 주문 생성 API */
  @PostMapping("/{participationId}/payment/checkout")
  public ResponseEntity<CreatePaymentOrderResponse> startPaymentCheckout(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    final PaymentOrderInfo paymentOrderInfo =
        buncheolCheckoutService.startPaymentCheckout(participantId, participationId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(CreatePaymentOrderResponse.from(paymentOrderInfo));
  }

  /**
   * 분철 낙찰자 (mock) 결제 확정 API. 결제 수단 확정 전까지는 호출 즉시 참여를 CONFIRMED 로 전환한다. 추후 실제 결제 수단(PG) 으로 교체한다.
   */
  @PostMapping("/{participationId}/payment")
  public ResponseEntity<Void> confirmMockPayment(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    buncheolCheckoutService.confirmMockPayment(participantId, participationId);
    return ResponseEntity.noContent().build();
  }

  /** 분철 낙찰자(구매자) 입금 완료 신고 API. AWAITING_PAYMENT 상태에서 입금 기한 내에만 가능하다. */
  @PostMapping("/{participationId}/payment/report")
  public ResponseEntity<Void> reportPayment(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    buncheolCheckoutService.reportPayment(participantId, participationId);
    return ResponseEntity.noContent().build();
  }

  /** 분철 개최자 수동 입금확인 API. 구매자가 신고한 참여(PAYMENT_REPORTED)를 개최자가 확인해 CONFIRMED 로 전환한다. */
  @PostMapping("/{participationId}/payment/confirm")
  public ResponseEntity<Void> confirmPayment(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long participationId) {
    hostPaymentService.confirmPayment(hostId, participationId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 분철 개최자의 미입금 낙찰자 만료 API. 입금 기한이 지난 AWAITING_PAYMENT 낙찰자를 FAILED 처리하고 차순위 후보를 입금대기로 승계한다. 승계 결과는
   * management 재조회로 확인한다.
   */
  @PostMapping("/{participationId}/payment/expire")
  public ResponseEntity<Void> expirePayment(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long participationId) {
    hostPaymentService.expirePayment(hostId, participationId);
    return ResponseEntity.noContent().build();
  }

  /** 분철 낙찰자(구매자) 본인의 결제 상세 조회 API. AWAITING_PAYMENT/PAYMENT_REPORTED 에서만 개최자 계좌를 노출한다. */
  @GetMapping("/{participationId}/payment")
  public ResponseEntity<ParticipationPaymentDetailResponse> getPaymentDetail(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    return ResponseEntity.ok(
        participationPaymentQueryService.getPaymentDetail(participantId, participationId));
  }

  /** 마이페이지 - 내가 참여한 분철 목록 조회 API. 최신 참여 순으로 정렬한다. */
  @GetMapping("/me")
  public ResponseEntity<List<MyParticipationResponse>> getMyParticipations(
      @AuthenticationPrincipal final Long participantId) {
    return ResponseEntity.ok(myParticipationQueryService.getMyParticipations(participantId));
  }

  /** 분철 참여 취소 API. 현재는 ACTIVE_BID 상태에서만 취소가 가능하다. */
  @DeleteMapping("/{participationId}")
  public ResponseEntity<Void> cancelParticipation(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    buncheolCheckoutService.cancelParticipation(participantId, participationId);
    return ResponseEntity.noContent().build();
  }
}
