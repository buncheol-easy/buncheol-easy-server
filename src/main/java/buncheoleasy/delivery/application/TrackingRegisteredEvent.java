package buncheoleasy.delivery.application;

/** 개최자가 운송장을 등록함(SNAPSHOTTED → SHIPPING). 참여자에게 발송 알림. */
public record TrackingRegisteredEvent(Long deliveryId) {}
