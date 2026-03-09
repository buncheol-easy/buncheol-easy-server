package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.dto.response.CreatePaymentOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/participations")
@RequiredArgsConstructor
public class ParticipationController {

  private final BuncheolCheckoutService buncheolCheckoutService;

  /** 분철 제시 잔금 결제 API */
  @PostMapping("/{participationId}/balance-payment/checkout")
  public ResponseEntity<CreatePaymentOrderResponse> startBalancePaymentCheckout(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    final PaymentOrderInfo paymentOrderInfo =
        buncheolCheckoutService.startBalancePaymentCheckout(participantId, participationId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(CreatePaymentOrderResponse.from(paymentOrderInfo));
  }
}
