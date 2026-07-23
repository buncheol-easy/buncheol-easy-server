package buncheoleasy.admin.application;

import buncheoleasy.admin.dto.response.AdminBulkResultResponse;
import buncheoleasy.delivery.application.DeliveryService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관리자 배송 벌크 처리(운송장 등록·수령완료). 배송 건별 독립 트랜잭션({@link DeliveryService#registerTrackingByAdmin},
 * {@link DeliveryService#confirmReceiptByAdmin})으로 처리하고 비즈니스 실패는 사유와 함께 모아 돌려준다 — 기존 프론트가 배송 건마다
 * 순차 호출하던 것을 단일 요청으로 대체한다.
 *
 * <p>앞 건들은 이미 커밋된 뒤라 중간에 500 으로 끊기면 프론트가 성공 범위를 알 수 없으므로, 예상 밖 예외도 실패 항목으로 수집해 응답 계약을 지킨다. 요청이
 * "같은 묶음배송의 배송 건들" 이라는 전제는 코드로 강제하지 않는다 — 관리자 도구 특성상 잘못 묶어도 운송장 재등록(SHIPPING 수렴)으로 복구 가능하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDeliveryCommandService {

  private final DeliveryService deliveryService;

  public AdminBulkResultResponse registerTracking(
      final List<Long> deliveryIds, final String trackingNumber) {
    return processEachDelivery(
        deliveryIds,
        deliveryId -> deliveryService.registerTrackingByAdmin(deliveryId, trackingNumber),
        "운송장 벌크 등록");
  }

  public AdminBulkResultResponse confirmReceipts(final List<Long> deliveryIds) {
    return processEachDelivery(deliveryIds, deliveryService::confirmReceiptByAdmin, "수령완료 벌크 처리");
  }

  // 같은 id 중복 요청은 순서를 유지한 채 제거한다 (무의미한 재처리와 성공/실패 이중 집계를 막는다).
  private AdminBulkResultResponse processEachDelivery(
      final List<Long> deliveryIds, final Consumer<Long> action, final String taskName) {
    final List<Long> succeededIds = new ArrayList<>();
    final List<AdminBulkResultResponse.Failure> failures = new ArrayList<>();

    for (final Long deliveryId : new LinkedHashSet<>(deliveryIds)) {
      try {
        action.accept(deliveryId);
        succeededIds.add(deliveryId);
      } catch (final BusinessException exception) {
        failures.add(
            new AdminBulkResultResponse.Failure(
                deliveryId,
                exception.getErrorCode().getCode(),
                exception.getErrorCode().getMessage()));
      } catch (final RuntimeException exception) {
        log.error("{} 중 예상 밖 실패. deliveryId={}", taskName, deliveryId, exception);
        failures.add(
            new AdminBulkResultResponse.Failure(
                deliveryId,
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
      }
    }
    return new AdminBulkResultResponse(succeededIds, failures);
  }
}
