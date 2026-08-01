package buncheoleasy.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Delivery Tracker 추적 콜백 본문 — 캐리어·운송장 번호만 오고 상태는 없다(수신 후 Track API 재조회). */
public record TrackingCallbackRequest(@NotBlank String carrierId, @NotBlank String trackingNumber) {}
