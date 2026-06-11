package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 낙찰자(구매자) 입금 완료 신고 요청.
 *
 * @param shippingAddressId 최종 선택 배송지 ID. 신고자 본인 소유이며 해당 분철이 지원하는 배송 방법이어야 한다.
 */
public record ReportPaymentRequest(@NotNull Long shippingAddressId) {}
