package buncheoleasy.buncheol.application.payback;

/** 배송비 환급 신청(재신청 포함) 커밋 후 운영자 슬랙 알림을 트리거하는 이벤트. */
public record ShippingFeePaybackRequestedEvent(Long participationId) {}
