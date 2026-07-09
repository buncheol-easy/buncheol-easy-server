package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminPaymentCommandService;
import buncheoleasy.admin.application.AdminPaymentQueryService;
import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.dto.request.AdminPaymentConfirmRequest;
import buncheoleasy.admin.dto.response.AdminBulkResultResponse;
import buncheoleasy.admin.dto.response.AdminPaymentRecordResponse;
import buncheoleasy.admin.dto.response.AdminPaymentSummaryResponse;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 결제 확인 대시보드 API. {@code /v1/admin/**} 은 SecurityConfig 가 ROLE_ADMIN 을 강제한다. */
@RestController
@RequestMapping("/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

  private final AdminPaymentQueryService adminPaymentQueryService;
  private final AdminPaymentCommandService adminPaymentCommandService;

  /**
   * 전체 분철의 결제(참여) 목록 조회. 최신 참여순 커서 페이지네이션이며, 파생 상태({@code status})·검색어({@code keyword}: 분철
   * 제목·그룹명·멤버명·참여자 닉네임 부분 일치)로 필터링할 수 있다.
   */
  @GetMapping
  public ResponseEntity<CursorResponse<AdminPaymentRecordResponse>> getPayments(
      @RequestParam(required = false) final AdminPaymentStatus status,
      @RequestParam(required = false) final String keyword,
      @RequestParam(required = false) final String cursor,
      @RequestParam(defaultValue = "20") final int size) {
    return ResponseEntity.ok(
        adminPaymentQueryService.getPayments(status, keyword, Cursor.parse(cursor), size));
  }

  /** 결제 대시보드 상단 통계 (파생 상태별 건수 + 확인 대기 금액 합계). */
  @GetMapping("/summary")
  public ResponseEntity<AdminPaymentSummaryResponse> getSummary() {
    return ResponseEntity.ok(adminPaymentQueryService.getSummary());
  }

  /** 입금확인 벌크 처리. 묶음 입금된 여러 참여를 한 번에 확인하고, 건별 성공/실패를 돌려준다. */
  @PostMapping("/confirm")
  public ResponseEntity<AdminBulkResultResponse> confirmPayments(
      @Valid @RequestBody final AdminPaymentConfirmRequest request) {
    return ResponseEntity.ok(
        adminPaymentCommandService.confirmPayments(request.participationIds()));
  }
}
