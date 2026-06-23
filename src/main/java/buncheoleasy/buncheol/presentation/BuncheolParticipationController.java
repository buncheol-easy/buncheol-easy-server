package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.participation.ParticipateResult;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.buncheol.dto.response.ParticipateResponse;
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

  private final ParticipationService participationService;

  /**
   * 분철 참여(멤버 슬롯 선착순 점유) API. 점유에 성공하면 입금확인중(AWAITING_PAYMENT) 상태로 등록되고, 응답으로 개최자 계좌·입금 총액·입금 만료
   * 시각을 받는다. 참여와 동시에 환불 계좌를 입력한다.
   */
  @PostMapping
  public ResponseEntity<ParticipateResponse> participate(
      @AuthenticationPrincipal final Long participantId,
      @PathVariable final Long buncheolId,
      @Valid @RequestBody final ParticipateRequest request) {
    final ParticipateResult result =
        participationService.participate(buncheolId, participantId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ParticipateResponse.from(result));
  }
}
