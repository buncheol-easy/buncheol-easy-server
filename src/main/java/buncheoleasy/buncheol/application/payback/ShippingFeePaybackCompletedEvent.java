package buncheoleasy.buncheol.application.payback;

/** 운영진의 배송비 환급 입금 완료 커밋 후 참여자 알림톡을 트리거하는 이벤트. */
public record ShippingFeePaybackCompletedEvent(Long participationId) {}
