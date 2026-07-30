package buncheoleasy.delivery.application;

/** 지점 도착 후 기한이 지나도록 미수령이라 독촉 알림이 필요함 (독촉 마킹 CAS 성공 시 1회만 발행). */
public record PickupReminderDueEvent(Long deliveryId) {}
