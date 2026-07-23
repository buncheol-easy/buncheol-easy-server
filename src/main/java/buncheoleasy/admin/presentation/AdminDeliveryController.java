package buncheoleasy.admin.presentation;

import buncheoleasy.admin.application.AdminDeliveryCommandService;
import buncheoleasy.admin.dto.request.AdminReceiptConfirmRequest;
import buncheoleasy.admin.dto.request.AdminTrackingRegistrationRequest;
import buncheoleasy.admin.dto.response.AdminBulkResultResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 배송 관리 API. {@code /v1/admin/**} 은 SecurityConfig 가 ROLE_ADMIN 을 강제한다. */
@RestController
@RequestMapping("/v1/admin/deliveries")
@RequiredArgsConstructor
public class AdminDeliveryController {

  private final AdminDeliveryCommandService adminDeliveryCommandService;

  /** 운송장 등록 벌크 처리. 같은 묶음배송의 여러 배송 건에 동일 운송장 번호를 등록하고, 건별 성공/실패를 돌려준다. */
  @PatchMapping("/tracking")
  public ResponseEntity<AdminBulkResultResponse> registerTracking(
      @Valid @RequestBody final AdminTrackingRegistrationRequest request) {
    return ResponseEntity.ok(
        adminDeliveryCommandService.registerTracking(
            request.deliveryIds(), request.trackingNumber()));
  }

  /** 수령완료 벌크 처리. 여러 배송 건을 한 번에 수령완료로 전이하고, 건별 성공/실패를 돌려준다. */
  @PostMapping("/receipt")
  public ResponseEntity<AdminBulkResultResponse> confirmReceipts(
      @Valid @RequestBody final AdminReceiptConfirmRequest request) {
    return ResponseEntity.ok(adminDeliveryCommandService.confirmReceipts(request.deliveryIds()));
  }
}
