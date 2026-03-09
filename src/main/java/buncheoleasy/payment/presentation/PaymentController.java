package buncheoleasy.payment.presentation;

import buncheoleasy.payment.application.PaymentService;
import buncheoleasy.payment.dto.request.CancelPaymentRequest;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @GetMapping("/success")
  public ResponseEntity<Void> success(
      @RequestParam final String paymentKey,
      @RequestParam("orderId") final String paymentOrderId,
      @RequestParam final long amount) {
    paymentService.confirmPayment(paymentKey, paymentOrderId, amount);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/fail")
  public ResponseEntity<Void> fail(
      @RequestParam(required = false) final String code,
      @RequestParam(required = false) final String message,
      @RequestParam(value = "orderId", required = false) final String paymentOrderId) {
    final URI redirectUri =
        UriComponentsBuilder.fromPath("/payment/fail.html")
            .queryParamIfPresent("orderId", Optional.ofNullable(paymentOrderId))
            .queryParamIfPresent("code", Optional.ofNullable(code))
            .queryParamIfPresent("message", Optional.ofNullable(message))
            .build()
            .encode()
            .toUri();

    return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
  }

  @PostMapping("/{paymentOrderId}/cancel")
  public ResponseEntity<Void> cancelPendingPayment(
      @AuthenticationPrincipal final Long participantId,
      @PathVariable final String paymentOrderId,
      @RequestBody(required = false) final CancelPaymentRequest request) {
    paymentService.cancelPendingPayment(
        participantId,
        paymentOrderId,
        request == null ? null : request.code(),
        request == null ? null : request.message());
    return ResponseEntity.noContent().build();
  }
}
