package buncheoleasy.buncheol.application.payback;

/**
 * 운영진의 배송비 환급 후기 반려 커밋 후 참여자 알림톡을 트리거하는 이벤트. 참여자는 사유를 보고 재신청할 수 있다.
 *
 * <p>반려 사유는 재조회하지 않고 이벤트에 스냅샷한다 — 반려는 terminal 이 아니라서(재신청 가능) 비동기 리스너가 재조회하기 전에 재신청이 끼어들면
 * 엔티티의 사유가 null 로 지워져 알림이 유실된다.
 */
public record ShippingFeePaybackRejectedEvent(Long participationId, String rejectReason) {}
