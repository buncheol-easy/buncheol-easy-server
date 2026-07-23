package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminShippingFeePaybackCommandService;
import buncheoleasy.admin.application.AdminShippingFeePaybackQueryService;
import buncheoleasy.admin.dto.request.AdminShippingFeePaybackActionRequest;
import buncheoleasy.admin.dto.response.AdminShippingFeePaybackResponse;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 배송비 환급(배송비 돌려받기) 검수 API. {@code /v1/admin/**} 은 SecurityConfig 가 ROLE_ADMIN 을 강제한다. */
@RestController
@RequestMapping("/v1/admin/shipping-fee-paybacks")
@RequiredArgsConstructor
public class AdminShippingFeePaybackController {

  private final AdminShippingFeePaybackQueryService adminShippingFeePaybackQueryService;
  private final AdminShippingFeePaybackCommandService adminShippingFeePaybackCommandService;

  /** 환급 신청 목록 조회. 신청 이력이 있는 참여만 신청 최신순으로 내려주며, 저장 상태로 필터링할 수 있다. */
  @GetMapping
  public ResponseEntity<CursorResponse<AdminShippingFeePaybackResponse>> getPaybacks(
      @RequestParam(required = false) final PaybackStatus status,
      @RequestParam(required = false) final String cursor,
      @RequestParam(defaultValue = "20") final int size) {
    return ResponseEntity.ok(
        adminShippingFeePaybackQueryService.getPaybacks(status, Cursor.parse(cursor), size));
  }

  /** 환급 검수 처리. COMPLETE = 입금 완료(승인·입금 한 번에), REJECT = 반려(사유 필수, 유저 재신청 가능). */
  @PatchMapping("/{participationId}")
  public ResponseEntity<Void> processPayback(
      @PathVariable final Long participationId,
      @Valid @RequestBody final AdminShippingFeePaybackActionRequest request) {
    adminShippingFeePaybackCommandService.process(participationId, request);
    return ResponseEntity.noContent().build();
  }
}
