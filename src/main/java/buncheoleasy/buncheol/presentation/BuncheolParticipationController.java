package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.application.ParticipationCheckoutInfo;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.buncheol.dto.response.ParticipationCheckoutResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/buncheols/{buncheolId}/participations")
@RequiredArgsConstructor
public class BuncheolParticipationController {

  private final BuncheolCheckoutService buncheolCheckoutService;

  /** 분철 참여 API */
  @PostMapping("/checkout")
  public ResponseEntity<ParticipationCheckoutResponse> startCheckout(
      @AuthenticationPrincipal final Long participantId,
      @PathVariable final Long buncheolId,
      @Valid @RequestBody final ParticipateRequest request) {
    final ParticipationCheckoutInfo checkoutInfo =
        buncheolCheckoutService.startCheckout(buncheolId, participantId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ParticipationCheckoutResponse.from(checkoutInfo));
  }
}
