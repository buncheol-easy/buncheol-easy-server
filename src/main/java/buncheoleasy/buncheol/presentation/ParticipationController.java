package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.participation.MyParticipationQueryService;
import buncheoleasy.buncheol.application.participation.ParticipationDetailQueryService;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackService;
import buncheoleasy.buncheol.dto.request.ShippingFeePaybackRequest;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.buncheol.dto.response.ParticipationDetailResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/participations")
@RequiredArgsConstructor
public class ParticipationController {

  private final ParticipationService participationService;
  private final ParticipationDetailQueryService participationDetailQueryService;
  private final MyParticipationQueryService myParticipationQueryService;
  private final ShippingFeePaybackService shippingFeePaybackService;

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

  /**
   * 개최자 수동 입금확인 API — <b>LEGACY(운영진 개최) 전용</b>. 입금확인중(AWAITING_PAYMENT) 참여를 입금 기한 내에
   * CONFIRMED 로 전환한다 (docs/46 §4.3).
   *
   * <p>🔴 <b>C2C 는 409(BUNCHEOL_FLOW_NOT_SUPPORTED) 다.</b> C2C 의 돈 단위는 참여 한 건이 아니라 묶음이라
   * (이체 1회·배송비 1회·택배 1개) 확정도 묶음 통째로여야 한다 — {@code POST
   * /v1/participation-bundles/{bundleId}/confirm} 을 쓴다. 슬롯 하나만 확정된 혼합 묶음은 「제외」·자동만료·
   * 「입금 수집 종료」·묶음 확정이 동시에 닫혀 되살릴 수 없다.
   */
  @PostMapping("/{participationId}/confirm")
  public ResponseEntity<Void> confirmPayment(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long participationId) {
    participationService.confirmPayment(hostId, participationId);
    return ResponseEntity.noContent().build();
  }

  /** C2C 참여자 "보냈어요" 마킹 API (docs/46 §4.2). 입금 후 마킹하면 만료 대상에서 제외되고 개최자 확인을 기다린다. 멱등. */
  @PostMapping("/{participationId}/payment-sent")
  public ResponseEntity<Void> markPaymentSent(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    participationService.markPaymentSent(participantId, participationId);
    return ResponseEntity.noContent().build();
  }

  /** C2C 참여자 마킹 철회 API — 오마킹 셀프 수정 (PAYMENT_SENT → AWAITING_PAYMENT 복귀, 기한 유지). 멱등. */
  @DeleteMapping("/{participationId}/payment-sent")
  public ResponseEntity<Void> revertPaymentSent(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    participationService.revertPaymentSent(participantId, participationId);
    return ResponseEntity.noContent().build();
  }

  /**
   * C2C 개최자 미입금 반려 API (docs/46 §4.5). 입금 내역을 찾지 못한 "보냈어요" 를 입금 대기로 되돌리고 기한을 연장(+24h)하며, 참여자에게
   * 입금 재확인 안내가 발송된다.
   */
  @PostMapping("/{participationId}/reject-payment")
  public ResponseEntity<Void> rejectPaymentSent(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long participationId) {
    participationService.rejectPaymentSent(hostId, participationId);
    return ResponseEntity.noContent().build();
  }

  /**
   * C2C 참여자 자발 취소 API (docs/46 §5 + docs/56 H-09). 신청(APPLIED)과 <b>성사 확정을 거치지 않은</b> 입금
   * 대기에서만 가능하다. 보냈어요·입금확인 이후는 문의 경유로 안내되고(BCH-086), 성사 확정을 거친 입금 대기는 개최자 연락으로
   * 안내된다(BCH-092). 추가 모집(docs/46 §4.7-E1)으로 바로 입금 대기가 된 참여는 확정을 거치지 않았으므로 계속 취소할 수 있다.
   */
  @DeleteMapping("/{participationId}")
  public ResponseEntity<Void> cancelParticipation(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long participationId) {
    participationService.cancelByParticipant(participantId, participationId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 배송비 환급(배송비 돌려받기) 신청 API. 이벤트 분철(0원 슬롯) 참여의 배송 완료 후, 후기 트윗 URL 을 제출해 환급을 신청한다. 반려된 신청의
   * 재신청도 같은 엔드포인트를 쓴다.
   */
  @PostMapping("/{participationId}/shipping-fee-payback")
  public ResponseEntity<Void> requestShippingFeePayback(
      @AuthenticationPrincipal final Long participantId,
      @PathVariable final Long participationId,
      @Valid @RequestBody final ShippingFeePaybackRequest request) {
    shippingFeePaybackService.request(participantId, participationId, request);
    return ResponseEntity.noContent().build();
  }
}
