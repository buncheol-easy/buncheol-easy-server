package buncheoleasy.deposit.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 페이액션 매칭완료 웹훅 본문. 금액은 실려 오지 않으므로 주문번호(= 참여 ID)로 우리 데이터를 조회해 판단한다.
 *
 * @param orderNumber 주문 등록 시 보낸 참여 ID
 * @param orderStatus 주문 상태 (확정 대상은 "매칭완료")
 * @param processingDate 페이액션 처리 시각 (ISO-8601, 로깅용)
 */
public record PayActionWebhookRequest(
    @JsonProperty("order_number") String orderNumber,
    @JsonProperty("order_status") String orderStatus,
    @JsonProperty("processing_date") String processingDate) {}
