package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.participation.MyParticipationQueryService;
import buncheoleasy.buncheol.application.participation.ParticipationDetailQueryService;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.buncheol.dto.response.ParticipationDetailResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

  private final ParticipationService participationService;
  private final ParticipationDetailQueryService participationDetailQueryService;
  private final MyParticipationQueryService myParticipationQueryService;

  /** 마이페이지 - 내가 참여한 분철 목록 조회 API. 최신 참여순으로 정렬한다. */
  @GetMapping("/me")
  public ResponseEntity<List<MyParticipationResponse>> getMyParticipations(
      @AuthenticationPrincipal final Long participantId) {
    return ResponseEntity.ok(myParticipationQueryService.getMyParticipations(participantId));
  }

  /** 참여자 본인의 참여 상세 조회 API. 입금확인중 단계에서는 입금할 개최자 계좌·총액·만료 시각을 노출한다. */
  @GetMapping("/{participationId}")
  public ResponseEntity<ParticipationDetailResponse> getParticipationDetail(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    return ResponseEntity.ok(
        participationDetailQueryService.getDetail(participantId, participationId));
  }

  /** 개최자 수동 입금확인 API. 입금확인중(AWAITING_PAYMENT) 참여를 입금 기한 내에 CONFIRMED 로 전환한다. */
  @PostMapping("/{participationId}/confirm")
  public ResponseEntity<Void> confirmPayment(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long participationId) {
    participationService.confirmPayment(hostId, participationId);
    return ResponseEntity.noContent().build();
  }

  /** 참여자 본인의 참여 취소 API. 입금확인중(AWAITING_PAYMENT) 단계에서만 취소가 가능하다. */
  @DeleteMapping("/{participationId}")
  public ResponseEntity<Void> cancelParticipation(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    participationService.cancelParticipation(participantId, participationId);
    return ResponseEntity.noContent().build();
  }
}
