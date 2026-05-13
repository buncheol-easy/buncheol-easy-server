package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.application.MyParticipationQueryService;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.dto.response.CreatePaymentOrderResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

  /** 마이페이지 - 내가 참여한 분철 목록 조회 API. 최신 참여 순으로 정렬한다. */
  @GetMapping("/me")
  public ResponseEntity<List<MyParticipationResponse>> getMyParticipations(
      @AuthenticationPrincipal final Long participantId) {
    return ResponseEntity.ok(myParticipationQueryService.getMyParticipations(participantId));
  }
}
