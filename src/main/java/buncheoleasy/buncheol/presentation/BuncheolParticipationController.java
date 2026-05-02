package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolCheckoutService;
import buncheoleasy.buncheol.domain.participation.Participation;
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

  /** 분철 참여 신청 API. 참여 즉시 ACTIVE_BID 상태로 등록되며, 결제는 마감 후 낙찰자에 한해 진행한다. */
  @PostMapping
  public ResponseEntity<ParticipationCheckoutResponse> participate(
      @AuthenticationPrincipal final Long participantId,
      @PathVariable final Long buncheolId,
      @Valid @RequestBody final ParticipateRequest request) {
    final Participation participation =
        buncheolCheckoutService.participate(buncheolId, participantId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ParticipationCheckoutResponse.from(participation));
  }
}
