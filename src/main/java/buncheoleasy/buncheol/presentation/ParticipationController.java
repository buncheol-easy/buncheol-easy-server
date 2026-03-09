package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.application.BuncheolParticipationService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.dto.request.UpdateBidRequest;
import buncheoleasy.buncheol.dto.response.ParticipationResponse;
import buncheoleasy.payment.application.PaymentOrderInfo;
import buncheoleasy.payment.dto.response.CreatePaymentOrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/participations")
@RequiredArgsConstructor
public class ParticipationController {

  private final BuncheolParticipationService buncheolParticipationService;
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

  /** 분철 제시 수정 API */
  @PatchMapping("/{participationId}/bid")
  public ResponseEntity<ParticipationResponse> updateBidParticipation(
      @AuthenticationPrincipal final Long participantId,
      @PathVariable final Long participationId,
      @Valid @RequestBody final UpdateBidRequest request) {
    final Participation participation =
        buncheolParticipationService.updateBidParticipation(
            participationId, participantId, request);
    return ResponseEntity.ok(ParticipationResponse.from(participation));
  }
}
