package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.participation.ParticipationBundleService;
import buncheoleasy.buncheol.dto.request.BundleConfirmRequest;
import buncheoleasy.buncheol.dto.response.BundleConfirmResponse;
import buncheoleasy.buncheol.dto.response.BundlePaymentSentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참여 <b>묶음</b> 단위 API. 묶음은 현실의 돈 단위(이체 1회 · 배송비 1회 · 택배 1개)이므로 조작도 그 단위로 연다 — 슬롯마다
 * 누르게 하면 개최자가 같은 신청을 여러 번 처리하게 되고, 그 사이에 묶음 상태가 갈린다.
 */
@RestController
@RequestMapping("/v1/participation-bundles")
@RequiredArgsConstructor
public class ParticipationBundleController {

  private final ParticipationBundleService participationBundleService;

  /**
   * 참여자 「보냈어요」 마킹 API — 묶음의 입금 대기 슬롯을 <b>한 번에</b> 표시한다.
   *
   * <p>묶음은 이체 1회의 단위다. 슬롯마다 누르게 하면 한 번 보낸 돈을 여러 번 신고하게 되고, 중간에 멈추면
   * 같은 묶음의 슬롯 상태가 갈린다 — 입금확인이 "묶음 안 슬롯은 갈리지 않는다" 를 전제한다.
   *
   * <p>기한이 지난 뒤에도 열려 있고, 이미 마킹된 묶음의 재요청은 멱등 성공한다.
   */
  @PostMapping("/{bundleId}/payment-sent")
  public ResponseEntity<BundlePaymentSentResponse> markPaymentSent(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long bundleId) {
    return ResponseEntity.ok(
        new BundlePaymentSentResponse(
            bundleId, participationBundleService.markPaymentSent(participantId, bundleId)));
  }

  /**
   * 개최자 입금확인 API — 묶음의 확인 가능 슬롯을 <b>한 번에</b> 확정한다 (all-or-nothing).
   *
   * <p>부분 확인은 애초에 성립하지 않는다 — 확인 API 에 금액이 없어 시스템은 실입금액을 모르고, 개최자가
   * 판단하는 것은 "이 이체가 들어왔는가" 하나뿐이다.
   *
   * <p>요청의 {@code expectedSlotIds} 에는 개최 관리 응답에서 {@code confirmTarget} 이 {@code true} 인
   * 슬롯만 담는다. 서버의 실제 집합과 다르면 409 로 막는다 — 개최자가 보지 못한 슬롯까지 확정되면 안 된다.
   */
  @PostMapping("/{bundleId}/confirm")
  public ResponseEntity<BundleConfirmResponse> confirmPayment(
      @AuthenticationPrincipal final Long hostId,
      @PathVariable final Long bundleId,
      @Valid @RequestBody final BundleConfirmRequest request) {
    return ResponseEntity.ok(
        new BundleConfirmResponse(
            bundleId,
            participationBundleService.confirmPayment(hostId, bundleId, request.expectedSlotIds())));
  }
}
